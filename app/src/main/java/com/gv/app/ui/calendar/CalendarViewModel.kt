package com.gv.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.CalendarRepository
import com.gv.app.data.repository.PlanFromEventRequest
import com.gv.app.data.repository.PlanRepository
import com.gv.app.data.repository.TaskRepository
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.UpdateEventRequest
import com.gv.app.ui.tasks.PickerTasks
import com.gv.app.ui.tasks.toPickerTasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which range is on screen. Everything else — events, title, day columns — derives from it. */
private data class VisibleRange(val mode: CalendarViewMode, val anchor: LocalDate) {
    /**
     * The half-open span of days the view covers. Deduplicating on *this* rather than on the
     * anchor matters: picking another day in the month grid moves the anchor without moving the
     * range, and keying on the anchor would refetch the same month on every tap.
     */
    fun bounds(): Pair<LocalDate, LocalDate> = rangeStart(mode, anchor) to rangeEnd(mode, anchor)
}

/**
 * Loading, error and plan state, kept apart so a refresh never rebuilds the event list. The plan
 * half is read live with each range and, unlike the events, not cached: offline it is simply
 * absent.
 */
private data class CalendarStatus(
    val loading: Boolean = true,
    val error: String? = null,
    val planBlocks: List<PlanBlockResponse> = emptyList(),
    /** The next seven days' capacity / busy / free hours. */
    val freeBusy: List<DayFreeBusy> = emptyList(),
)

data class CalendarUiState(
    val mode: CalendarViewMode = CalendarViewMode.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val title: String = "",
    val days: List<LocalDate> = emptyList(),
    val source: CalendarSource = CalendarSource.BOTH,
    /** What the views draw: events and/or plan blocks, according to [source]. */
    val items: List<AgendaItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val planBlocks: List<PlanBlockResponse> = emptyList(),
    val freeBusy: List<DayFreeBusy> = emptyList(),
) {
    /** Whether an event already has a plan block. */
    fun hasPlan(eventId: Int): Boolean = planBlocks.any { it.event_ref == eventId.toString() }

    val isCurrentPeriod: Boolean
        get() {
            val today = LocalDate.now()
            return !today.isBefore(rangeStart(mode, anchor)) && today.isBefore(rangeEnd(mode, anchor))
        }
}

/**
 * Calendar tab state.
 *
 * Events are collected from the Room cache, which is what makes the calendar readable offline;
 * the fetch reconciles it. Nothing is patched in place from a write's response: the range is
 * re-read. Writes are refused offline by the repository, and the refusal is spoken through
 * [toast] — a tap that silently does nothing reads as a bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: CalendarRepository = app.container.calendarRepository
    private val planRepo: PlanRepository = app.container.planRepository
    private val taskRepo: TaskRepository = app.container.taskRepository
    private val zone: ZoneId = ZoneId.systemDefault()
    private val prefs = app.getSharedPreferences("gv_calendar", android.content.Context.MODE_PRIVATE)

    private val range = MutableStateFlow(VisibleRange(CalendarViewMode.MONTH, LocalDate.now()))
    private val status = MutableStateFlow(CalendarStatus())
    private val source = MutableStateFlow(
        runCatching { CalendarSource.valueOf(prefs.getString("source", null) ?: "BOTH") }
            .getOrDefault(CalendarSource.BOTH),
    )

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val bounds: Flow<Pair<LocalDate, LocalDate>> = range.map { it.bounds() }.distinctUntilChanged()

    private val eventsInRange: Flow<List<CalendarEvent>> = bounds
        .flatMapLatest { (from, to) -> repo.eventsBetween(instantAt(from), instantAt(to)) }

    val state: StateFlow<CalendarUiState> = combine(
        range,
        eventsInRange,
        source,
        status,
    ) { r, events, src, s ->
        CalendarUiState(
            mode = r.mode,
            anchor = r.anchor,
            title = rangeTitle(r.mode, r.anchor),
            days = rangeDays(r.mode, r.anchor),
            source = src,
            items = buildItems(events, s.planBlocks, src),
            loading = s.loading,
            error = s.error,
            planBlocks = s.planBlocks,
            freeBusy = s.freeBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private var refreshJob: Job? = null

    init {
        // Every range change re-reads that range; collectLatest drops a slow earlier fetch so a
        // quick swipe cannot land the wrong month on screen.
        viewModelScope.launch {
            bounds.collectLatest { (from, to) -> fetchRange(from, to) }
        }
    }

    // --- navigation --------------------------------------------------------------------

    fun setMode(mode: CalendarViewMode) {
        range.update { it.copy(mode = mode) }
    }

    fun setSource(value: CalendarSource) {
        source.value = value
        prefs.edit().putString("source", value.name).apply()
    }

    fun shift(direction: Int) {
        range.update { it.copy(anchor = shiftAnchor(it.mode, it.anchor, direction)) }
    }

    fun goToday() {
        range.value = range.value.copy(anchor = LocalDate.now())
    }

    /** Tapping a day in the month grid drills into it, like the web. */
    fun openDay(day: LocalDate) {
        range.value = VisibleRange(CalendarViewMode.DAY, day)
    }

    /** Picks a day inside the month view without leaving it. */
    fun openDayInMonth(day: LocalDate) {
        range.update { it.copy(anchor = day) }
    }

    // --- data -------------------------------------------------------------------------

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { fetchCurrentRange() }
    }

    private suspend fun fetchRange(from: LocalDate, to: LocalDate) {
        status.update { it.copy(loading = true) }
        // The events and the plan are independent reads; a failed plan read keeps what was there.
        val today = LocalDate.now()
        val (result, blocks, freeBusy) = coroutineScope {
            val events = async { repo.refreshRange(instantAt(from), instantAt(to)) }
            val plan = async { planRepo.blocksBetween(from, to) }
            val capacity = async { planRepo.freeBusy(today, today.plusDays(7)) }
            Triple(events.await(), plan.await(), capacity.await())
        }
        status.update {
            it.copy(
                planBlocks = (blocks as? ApiResult.Success)?.data ?: it.planBlocks,
                freeBusy = (freeBusy as? ApiResult.Success)?.data ?: it.freeBusy,
                loading = false,
                // Being offline is a normal state with its own banner; only a real server
                // answer is worth an error line over the grid.
                error = (result as? ApiResult.Failure)?.takeIf { f -> !f.offline }?.message,
            )
        }
    }

    private suspend fun fetchCurrentRange() {
        val (from, to) = range.value.bounds()
        fetchRange(from, to)
    }

    private fun instantAt(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    // --- event writes ------------------------------------------------------------------

    /*
     * Every write reports back through `onResult(ok)`, success or not. A callback that only fires
     * on success leaves the form's button disabled forever the one time the write fails, which is
     * exactly when the user wants to try again.
     */

    fun createEvent(request: CreateEventRequest, onResult: (Boolean) -> Unit) {
        write(onResult, { repo.createEvent(request) }, "Event created")
    }

    fun updateEvent(id: Int, request: UpdateEventRequest, onResult: (Boolean) -> Unit) {
        write(onResult, { repo.updateEvent(id, request) }, "Event updated")
    }

    fun deleteEvent(id: Int, onResult: (Boolean) -> Unit) {
        write(onResult, { repo.deleteEvent(id) }, "Event deleted")
    }

    /**
     * "Create plan" on an event. [onResult] gets the failure's message (null on success) so the
     * sheet can show it *inside* itself: the snackbar sits under a modal sheet's scrim, and a
     * refusal — a block that overlaps one already in the plan, say — would otherwise go unseen.
     */
    fun createPlanFromEvent(event: CalendarEvent, plan: PlanFromEventCheck.Ok, onResult: (error: String?) -> Unit) {
        val request = PlanFromEventRequest(
            startsAt = plan.startsAt,
            endsAt = plan.endsAt,
            taskId = plan.taskId,
            newTaskName = plan.newTaskName,
            label = plan.label,
            moveEvent = plan.moveEvent,
        )
        viewModelScope.launch {
            when (val r = planRepo.createFromEvent(event, request)) {
                is ApiResult.Success -> {
                    onResult(null)
                    fetchCurrentRange()
                    _toast.emit("Plan created")
                }

                is ApiResult.Failure -> {
                    // The range is re-read even so: an event rescheduled before the block failed
                    // has changed, and the screen should say so.
                    if (plan.moveEvent) fetchCurrentRange()
                    onResult(r.message)
                }
            }
        }
    }

    /** The task picker's list, read live each time it opens. */
    suspend fun pickerTasks(): PickerTasks = taskRepo.pickerTasks().toPickerTasks()

    private fun write(
        onResult: (Boolean) -> Unit,
        call: suspend () -> ApiResult<*>,
        success: String,
    ) {
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Success -> {
                    onResult(true)
                    fetchCurrentRange()
                    _toast.emit(success)
                }

                is ApiResult.Failure -> {
                    onResult(false)
                    _toast.emit(r.message)
                }
            }
        }
    }
}
