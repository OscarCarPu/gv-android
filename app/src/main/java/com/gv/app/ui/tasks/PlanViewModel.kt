package com.gv.app.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.PlanRepository
import com.gv.app.data.repository.TaskRepository
import com.gv.app.domain.model.CreateCommitmentRequest
import com.gv.app.domain.model.CreatePlanBlockRequest
import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.RecurringCommitmentResponse
import com.gv.app.domain.model.UpdateCommitmentRequest
import com.gv.app.domain.model.UpdatePlanBlockRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the plan section shows. Today gets the timeline; any other day is a plain block list. */
data class PlanUiState(
    val date: LocalDate = LocalDate.now(),
    /** False while today's plan has never loaded (a failed first fetch reads as "could not load"). */
    val loaded: Boolean = false,
    val timeline: PlanTimeline? = null,
    val summary: PlanSummary? = null,
    /** Another day's blocks; null while they load. Unused when [isToday]. */
    val otherDayBlocks: List<PlanBlockResponse>? = null,
    val freeBusy: List<DayFreeBusy> = emptyList(),
    /** Whether there is anything to draw: a block or a logged entry. */
    val hasContent: Boolean = false,
) {
    val isToday: Boolean get() = date == LocalDate.now()
}

/**
 * Owns Today's Plan: the actual-over-intent timeline, its budget, browsing another day, and the
 * per-block writes. The plan comes from the same cached snapshot as the rest of the Tasks
 * screen; this class only adds the clock, the day picker and the writes.
 */
class PlanViewModel(app: Application) : AndroidViewModel(app) {

    private val tasks: TaskRepository = app.container.taskRepository
    private val plan: PlanRepository = app.container.planRepository

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val otherDay = MutableStateFlow<List<PlanBlockResponse>?>(null)

    /**
     * A once-a-minute tick so the "now" line advances. The timeline is rebuilt whenever the
     * snapshot changes as well, and reads the clock at that moment, so a timer started or
     * stopped a second ago is never dated after "now" and dropped.
     */
    private val tick = MutableStateFlow(0)

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    val state: StateFlow<PlanUiState> =
        combine(tasks.tasksData(), selectedDate, otherDay, tick) { data, date, other, _ ->
            val today = data?.plan
            val timeline = today?.let {
                buildPlanTimeline(it.blocks, data.todayEntries, System.currentTimeMillis())
            }
            PlanUiState(
                date = date,
                loaded = today != null,
                timeline = timeline,
                summary = if (today != null && timeline != null) {
                    buildPlanSummary(timeline, today.budget.daily_target_seconds, today.totals.free_seconds)
                } else null,
                otherDayBlocks = other,
                freeBusy = data?.freeBusy.orEmpty(),
                hasContent = today != null && (today.blocks.isNotEmpty() || data.todayEntries.isNotEmpty()),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                tick.value += 1
            }
        }
    }

    // ----- Day selection -----

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        if (date == LocalDate.now()) otherDay.value = null else loadOtherDay(date)
    }

    fun backToToday() = selectDate(LocalDate.now())

    private fun loadOtherDay(date: LocalDate) {
        otherDay.value = null
        viewModelScope.launch {
            when (val r = plan.blocksOn(date)) {
                is ApiResult.Success -> otherDay.value = r.data
                is ApiResult.Failure -> {
                    _toast.emit(r.message)
                    otherDay.value = emptyList()
                }
            }
        }
    }

    /** After a write: today is refreshed by the repository; another day is re-read here. */
    private fun reloadSelectedDay() {
        val date = selectedDate.value
        if (date != LocalDate.now()) loadOtherDay(date)
    }

    // ----- Block actions -----

    /** Start, Renew or Done on the task a block points at — whichever the task is ready for. */
    fun toggleBlock(b: PlanBlockResponse) {
        val taskId = b.task_id ?: return
        viewModelScope.launch {
            val result = if (b.task_started_at == null) {
                tasks.startTask(taskId)
            } else {
                tasks.finishOrRenew(taskId, b.task_type, b.task_recurrence)
            }
            report(result)
        }
    }

    fun deleteBlock(b: PlanBlockResponse) {
        viewModelScope.launch {
            report(plan.deleteBlock(b.id))
            reloadSelectedDay()
        }
    }

    fun clearFuture() {
        viewModelScope.launch { report(plan.clearFutureBlocks()) }
    }

    fun createBlock(request: CreatePlanBlockRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = plan.createBlock(request)
            report(result)
            reloadSelectedDay()
            onDone(result is ApiResult.Success)
        }
    }

    fun updateBlock(id: Int, request: UpdatePlanBlockRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = plan.updateBlock(id, request)
            report(result)
            reloadSelectedDay()
            onDone(result is ApiResult.Success)
        }
    }

    // ----- Commitments (read live; nothing is cached for them) -----

    suspend fun commitments(): List<RecurringCommitmentResponse>? =
        when (val r = plan.commitments()) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> {
                _toast.emit(r.message)
                null
            }
        }

    fun createCommitment(request: CreateCommitmentRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = plan.createCommitment(request)
            report(result)
            onDone(result is ApiResult.Success)
        }
    }

    fun updateCommitment(id: Int, request: UpdateCommitmentRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = plan.updateCommitment(id, request)
            report(result)
            onDone(result is ApiResult.Success)
        }
    }

    fun deleteCommitment(id: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = plan.deleteCommitment(id)
            report(result)
            onDone(result is ApiResult.Success)
        }
    }

    private suspend fun report(result: ApiResult<*>) {
        if (result is ApiResult.Failure) _toast.emit(result.message)
    }
}
