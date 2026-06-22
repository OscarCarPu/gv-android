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
 * Offline-first: each day's list is collected from the Room cache (instant, works offline) and
 * reconciled by a background fetch when that day is selected. Logging writes through the
 * repository, which commits locally and queues the sync — so taps never block on the network.
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
        viewModelScope.launch { repo.adjustHabit(habitId, _selectedDate.value, delta) }
    }

    fun onSetValue(habitId: Int, value: Double) {
        viewModelScope.launch { repo.setHabit(habitId, _selectedDate.value, value) }
    }

    fun onDelete(habitId: Int) {
        viewModelScope.launch { repo.deleteHabit(habitId) }
    }
}
