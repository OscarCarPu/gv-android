package com.gv.app.ui.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.HabitRepository
import com.gv.app.domain.model.CreateHabitRequest
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.UpdateHabitRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Online-first: each day's list is collected from the Room cache (so it paints instantly and
 * still reads offline) and reconciled by a fetch when that day is selected. Writes go straight
 * through to the server; offline they are refused, and the repository's failure message is what
 * the toast shows.
 *
 * Logging is the one place the screen answers before the server does. A tap sets a *pending*
 * value that the card shows at once (and recomputes its progress from); the write itself waits
 * [LOG_DEBOUNCE_MS] so a run of taps is one request carrying the final absolute value, and the
 * pending value is dropped once the day has been re-read — or on failure, which puts the card
 * back on what the server holds.
 */
class HabitsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: HabitRepository = app.container.habitRepository

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _refreshing = MutableStateFlow(true)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _pending = MutableStateFlow<Map<HabitLogKey, Double>>(emptyMap())

    /** Values entered but not yet confirmed by the server, per habit per day. */
    val pending: StateFlow<Map<HabitLogKey, Double>> = _pending.asStateFlow()

    private val debounce = mutableMapOf<HabitLogKey, Job>()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** Cache flow for an arbitrary day, so each swipe page renders its own day from cache. */
    fun habitsFor(date: LocalDate): Flow<List<HabitWithLog>> = repo.habitsForDate(date)

    init {
        viewModelScope.launch {
            _selectedDate.collectLatest { date -> refreshDate(date) }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshDate(_selectedDate.value) }
    }

    private suspend fun refreshDate(date: LocalDate) {
        _refreshing.value = true
        val result = repo.refreshDate(date)
        _refreshing.value = false
        // The offline banner already communicates connectivity; only surface real server errors.
        if (result is ApiResult.Failure && result.code != null && result.code in 400..599) {
            _toast.emit(result.message)
        }
    }

    fun onPrevDay() = onDateChange(_selectedDate.value.minusDays(1))
    fun onNextDay() = onDateChange(_selectedDate.value.plusDays(1))
    fun onToday() = onDateChange(LocalDate.now())

    fun onDateChange(date: LocalDate) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
    }

    /** The +/- buttons: relative to what the card is showing, which may be a pending value. */
    fun onAdjust(habit: HabitWithLog, date: LocalDate, delta: Double) {
        val shown = displayValue(habit, _pending.value[HabitLogKey(habit.id, date)])
        log(habit.id, date, shown + delta)
    }

    /** The typed value. */
    fun onSetValue(habitId: Int, date: LocalDate, value: Double) = log(habitId, date, value)

    private fun log(habitId: Int, date: LocalDate, requested: Double) {
        val key = HabitLogKey(habitId, date)
        val value = requested.coerceAtLeast(0.0)
        _pending.update { it + (key to value) }
        debounce.remove(key)?.cancel()
        debounce[key] = viewModelScope.launch {
            delay(LOG_DEBOUNCE_MS)
            val result = repo.setHabit(habitId, date, value)
            // Only if nothing newer was entered while this was in flight; otherwise the newer
            // value is still pending and its own write will clear it.
            _pending.update { if (it[key] == value) it - key else it }
            report(result)
        }
    }

    /** Create or update. [onDone] gets whether it worked, so the sheet only closes on success. */
    fun onSave(existing: HabitWithLog?, form: HabitFormCheck.Ok, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val result = if (existing == null) {
                repo.createHabit(
                    CreateHabitRequest(
                        name = form.name,
                        description = form.description,
                        frequency = form.frequency,
                        target_min = form.targetMin,
                        target_max = form.targetMax,
                        recording_required = form.recordingRequired,
                    ),
                    date,
                )
            } else {
                repo.updateHabit(
                    existing.id,
                    UpdateHabitRequest(
                        name = form.name,
                        description = form.description,
                        frequency = form.frequency,
                        target_min = form.targetMin,
                        target_max = form.targetMax,
                        recording_required = form.recordingRequired,
                    ),
                    date,
                )
            }
            report(result)
            onDone(result is ApiResult.Success)
        }
    }

    fun onDelete(habitId: Int) {
        viewModelScope.launch { report(repo.deleteHabit(habitId)) }
    }

    /**
     * Writes are the one place the offline refusal must be spoken aloud: the banner explains
     * the state, but a tap that quietly does nothing reads as a bug.
     */
    private suspend fun report(result: ApiResult<*>) {
        if (result is ApiResult.Failure) _toast.emit(result.message)
    }
}

/** One habit on one day: what a pending value is pending *for*. */
data class HabitLogKey(val habitId: Int, val date: LocalDate)

/** Quiet time after the last tap before the write goes out. */
private const val LOG_DEBOUNCE_MS = 300L
