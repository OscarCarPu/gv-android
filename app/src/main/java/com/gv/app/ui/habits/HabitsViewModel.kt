package com.gv.app.ui.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.HabitRepository
import com.gv.app.domain.model.HabitWithLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Online-first: each day's list is collected from the Room cache (so it paints instantly and
 * still reads offline) and reconciled by a fetch when that day is selected. Logging writes
 * straight through to the server; offline it is refused, and the repository's failure message
 * is what the toast shows.
 */
class HabitsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: HabitRepository = app.container.habitRepository

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _refreshing = MutableStateFlow(true)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

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

    fun onAdjust(habitId: Int, delta: Double) {
        viewModelScope.launch { report(repo.adjustHabit(habitId, _selectedDate.value, delta)) }
    }

    fun onSetValue(habitId: Int, value: Double) {
        viewModelScope.launch { report(repo.setHabit(habitId, _selectedDate.value, value)) }
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
