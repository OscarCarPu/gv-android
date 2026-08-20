package com.gv.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.api.CalendarStreamEvent
import com.gv.app.data.api.CalendarStreamMessage
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.CalendarRepository
import com.gv.app.domain.model.CalendarAccount
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.GoogleCalendar
import com.gv.app.domain.model.UpdateEventRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

/** Loading, error and stream state, kept apart so a refresh never rebuilds the event list. */
private data class CalendarStatus(
    val loading: Boolean = true,
    val error: String? = null,
    val live: Boolean = false,
)

data class CalendarUiState(
    val mode: CalendarViewMode = CalendarViewMode.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val title: String = "",
    val days: List<LocalDate> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val calendars: List<GoogleCalendar> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** True while the change stream is connected, so the header can say the view is live. */
    val live: Boolean = false,
) {
    /** The calendars an event may actually be created on. */
    val writableCalendars: List<GoogleCalendar>
        get() = calendars.filter { it.writable && !it.deleted && it.sync_enabled }

    val defaultCalendarId: Int?
        get() = (writableCalendars.firstOrNull { it.is_primary } ?: writableCalendars.firstOrNull())?.id

    fun calendarById(id: Int): GoogleCalendar? = calendars.firstOrNull { it.id == id }

    val isCurrentPeriod: Boolean
        get() {
            val today = LocalDate.now()
            return !today.isBefore(rangeStart(mode, anchor)) && today.isBefore(rangeEnd(mode, anchor))
        }
}

/**
 * Calendar tab state.
 *
 * Three things change what is on screen — navigating, toggling a calendar, and a push
 * notification arriving over the change stream — and all three funnel into one refetch of one
 * range, so a live update and a freshly opened tab take the same code path. Nothing is patched
 * in place from a write's response: Google rewrites what it is given (a "following" split moves
 * the occurrence into a new series with a new id, a cross-account move recreates the event), so
 * the range is re-read instead of guessed at.
 *
 * Events are collected from the Room cache, which is what makes the calendar readable offline;
 * the fetch reconciles it. Writes are refused offline by the repository, and the refusal is
 * spoken through [toast] — a tap that silently does nothing reads as a bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: CalendarRepository = app.container.calendarRepository
    private val zone: ZoneId = ZoneId.systemDefault()

    private val range = MutableStateFlow(VisibleRange(CalendarViewMode.MONTH, LocalDate.now()))
    private val status = MutableStateFlow(CalendarStatus())

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** Google's consent page, to be opened in the browser by the screen. */
    private val _openUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl: SharedFlow<String> = _openUrl.asSharedFlow()

    private val _accounts = MutableStateFlow<List<CalendarAccount>>(emptyList())
    val accounts: StateFlow<List<CalendarAccount>> = _accounts.asStateFlow()

    private val bounds: Flow<Pair<LocalDate, LocalDate>> = range.map { it.bounds() }.distinctUntilChanged()

    private val eventsInRange: Flow<List<CalendarEvent>> = bounds
        .flatMapLatest { (from, to) -> repo.eventsBetween(instantAt(from), instantAt(to)) }

    val state: StateFlow<CalendarUiState> = combine(
        range,
        eventsInRange,
        repo.calendars(),
        status,
    ) { r, events, calendars, s ->
        CalendarUiState(
            mode = r.mode,
            anchor = r.anchor,
            title = rangeTitle(r.mode, r.anchor),
            days = rangeDays(r.mode, r.anchor),
            events = events,
            calendars = calendars,
            loading = s.loading,
            error = s.error,
            live = s.live,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private var refreshJob: Job? = null
    private var liveJob: Job? = null
    private var coalesceJob: Job? = null

    init {
        // Every range change re-reads that range; collectLatest drops a slow earlier fetch so a
        // quick swipe cannot land the wrong month on screen.
        viewModelScope.launch {
            bounds.collectLatest { (from, to) -> fetchRange(from, to) }
        }
        viewModelScope.launch { repo.refreshCalendars() }
    }

    // --- navigation --------------------------------------------------------------------

    fun setMode(mode: CalendarViewMode) {
        range.update { it.copy(mode = mode) }
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

    /**
     * Picks a day inside the month view without leaving it: the grid is a picker for the agenda
     * below it, and the whole month is already loaded, so this only moves which day is read.
     */
    fun openDayInMonth(day: LocalDate) {
        range.update { it.copy(anchor = day) }
    }

    // --- data -------------------------------------------------------------------------

    /**
     * Reconciles both halves of what is on screen. The calendar list is included because this
     * also runs when the screen comes back — which is how an account connected in the browser
     * turns up here, there being no callback into the app to wait for.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repo.refreshCalendars()
            val (from, to) = range.value.bounds()
            fetchRange(from, to)
        }
    }

    private suspend fun fetchRange(from: LocalDate, to: LocalDate) {
        status.update { it.copy(loading = true) }
        val result = repo.refreshRange(instantAt(from), instantAt(to))
        status.update {
            it.copy(
                loading = false,
                // Being offline is a normal state with its own banner; only a real server
                // answer is worth an error line over the grid.
                error = (result as? ApiResult.Failure)?.takeIf { f -> !f.offline }?.let { f -> explain(f) },
            )
        }
    }

    private suspend fun fetchCurrentRange() {
        val (from, to) = range.value.bounds()
        fetchRange(from, to)
    }

    private fun instantAt(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    // --- live updates -----------------------------------------------------------------

    /**
     * Subscribes to the change stream. Started and stopped with the screen rather than the
     * ViewModel: the ViewModel outlives the tab (it is scoped to the Activity), and a socket held
     * open for a tab nobody is looking at is a socket held open all day.
     */
    fun startLiveUpdates() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            repo.liveUpdates().collect { event ->
                when (event) {
                    is CalendarStreamEvent.Connected -> status.update { it.copy(live = true) }
                    is CalendarStreamEvent.Disconnected -> status.update { it.copy(live = false) }
                    is CalendarStreamEvent.Changed -> onStreamMessage(event.message)
                }
            }
        }
    }

    fun stopLiveUpdates() {
        liveJob?.cancel()
        liveJob = null
        coalesceJob?.cancel()
        coalesceJob = null
        status.update { it.copy(live = false) }
    }

    private fun onStreamMessage(message: CalendarStreamMessage) {
        when (message.type) {
            CalendarStreamMessage.ACCOUNT_NEEDS_REAUTH -> {
                viewModelScope.launch {
                    repo.refreshCalendars()
                    val who = message.account_email.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                    _toast.emit("A Google account needs to be reconnected$who")
                }
            }

            CalendarStreamMessage.ACCOUNT_CONNECTED, CalendarStreamMessage.ACCOUNT_DISCONNECTED -> {
                viewModelScope.launch { repo.refreshCalendars() }
                scheduleCoalescedRefresh()
            }

            else -> scheduleCoalescedRefresh()
        }
    }

    /** One notification per change is the ideal; a burst is normal. Coalesce them. */
    private fun scheduleCoalescedRefresh() {
        coalesceJob?.cancel()
        coalesceJob = viewModelScope.launch {
            delay(COALESCE_MS)
            fetchCurrentRange()
        }
    }

    // --- calendars & accounts ----------------------------------------------------------

    fun toggleCalendarVisible(calendar: GoogleCalendar) {
        viewModelScope.launch {
            when (val r = repo.setCalendarVisible(calendar.id, !calendar.visible)) {
                is ApiResult.Success -> fetchCurrentRange()
                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    fun toggleCalendarSync(calendar: GoogleCalendar) {
        val next = !calendar.sync_enabled
        viewModelScope.launch {
            when (val r = repo.setCalendarSync(calendar.id, next)) {
                is ApiResult.Success -> {
                    _toast.emit(if (next) "Syncing ${calendar.summary}" else "Stopped syncing ${calendar.summary}")
                    fetchCurrentRange()
                }

                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            when (val r = repo.syncNow()) {
                is ApiResult.Success -> {
                    repo.refreshCalendars()
                    fetchCurrentRange()
                    val firstError = r.data.errors?.firstOrNull()
                    _toast.emit(
                        when {
                            firstError != null -> firstError
                            r.data.upserted > 0 || r.data.deleted > 0 ->
                                "Synced: ${r.data.upserted} updated, ${r.data.deleted} removed"

                            else -> "Already up to date"
                        },
                    )
                }

                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            when (val r = repo.listAccounts()) {
                is ApiResult.Success -> _accounts.value = r.data
                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    /**
     * Starts adding a Google account. The consent screen is Google's own and its redirect lands
     * on gv-api and then on gv-web, so it happens in the browser and this app finds out by
     * reloading afterwards — there is no callback back into the app to wait for.
     */
    fun connectAccount() {
        viewModelScope.launch {
            when (val r = repo.authUrl()) {
                is ApiResult.Success -> _openUrl.emit(r.data)
                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    fun resyncAccount(account: CalendarAccount) {
        viewModelScope.launch {
            when (val r = repo.resyncAccount(account.id)) {
                is ApiResult.Success -> {
                    repo.refreshCalendars()
                    fetchCurrentRange()
                    loadAccounts()
                    _toast.emit("Rebuilt ${account.email}: ${r.data.upserted} events in ${r.data.calendars} calendars")
                }

                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    fun deleteAccount(account: CalendarAccount) {
        viewModelScope.launch {
            when (val r = repo.deleteAccount(account.id)) {
                is ApiResult.Success -> {
                    loadAccounts()
                    fetchCurrentRange()
                    _toast.emit("Disconnected ${account.email}")
                }

                is ApiResult.Failure -> _toast.emit(explain(r))
            }
        }
    }

    // --- event writes ------------------------------------------------------------------

    /*
     * Every write reports back through `onResult(ok)`, success or not. A callback that only fires
     * on success leaves the form's button disabled forever the one time the write fails, which is
     * exactly when the user wants to try again.
     */

    fun createEvent(request: CreateEventRequest, onResult: (Boolean) -> Unit) {
        write(onResult, { repo.createEvent(request) }, "Event created")
    }

    fun updateEvent(ref: String, request: UpdateEventRequest, onResult: (Boolean) -> Unit) {
        write(onResult, { repo.updateEvent(ref, request) }, "Event updated")
    }

    fun deleteEvent(ref: String, scope: String?, sendUpdates: String?, onResult: (Boolean) -> Unit) {
        write(onResult, { repo.deleteEvent(ref, scope, sendUpdates) }, "Event deleted")
    }

    fun moveEvent(ref: String, calendarId: Int, sendUpdates: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.moveEvent(ref, calendarId, sendUpdates)) {
                is ApiResult.Success -> {
                    onResult(true)
                    fetchCurrentRange()
                    _toast.emit(
                        if (r.data.recreated) {
                            "Moved to another account — the event was recreated there"
                        } else {
                            "Event moved"
                        },
                    )
                }

                is ApiResult.Failure -> {
                    onResult(false)
                    _toast.emit(explain(r))
                }
            }
        }
    }

    private fun write(
        onResult: (Boolean) -> Unit,
        call: suspend () -> ApiResult<*>,
        success: String,
    ) {
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Success -> {
                    onResult(true)
                    // Re-read rather than patch: Google rewrites what it is given, so the range
                    // is the only thing that knows what the event became.
                    fetchCurrentRange()
                    _toast.emit(success)
                }

                is ApiResult.Failure -> {
                    onResult(false)
                    _toast.emit(explain(r))
                }
            }
        }
    }

    /** Turns the API's answers into something worth reading, mirroring gv-web's wording. */
    private fun explain(failure: ApiResult.Failure): String {
        val message = failure.message
        return when {
            message.contains("refetch and retry") ->
                "This event changed in Google while you were editing it. Refresh and try again."

            message.contains("reconnected") -> "That Google account needs to be reconnected."
            message.contains("read-only") -> "That calendar is read-only in Google."
            failure.code == 503 -> "Google is not configured on the server."
            else -> message
        }
    }

    private companion object {
        const val COALESCE_MS = 400L
    }
}
