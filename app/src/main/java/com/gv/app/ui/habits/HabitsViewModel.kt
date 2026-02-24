package com.gv.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.domain.model.Habit
import com.gv.app.domain.model.LogRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class HabitsUiState {
    object Loading : HabitsUiState()
    data class Success(val habits: List<Habit>) : HabitsUiState()
    data class Error(val message: String) : HabitsUiState()
}

class HabitsViewModel(
    private val api: ApiService = RetrofitClient.apiService
) : ViewModel() {

    private val dateFormat  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFmt  = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    private val calendar    = Calendar.getInstance()
    private val todayStr    = dateFormat.format(Date())

    private val _dateStr     = MutableStateFlow(todayStr)
    private val _displayDate = MutableStateFlow(displayFmt.format(calendar.time))
    private val _isToday     = MutableStateFlow(true)

    val dateStr:     StateFlow<String>  = _dateStr
    val displayDate: StateFlow<String>  = _displayDate
    val isToday:     StateFlow<Boolean> = _isToday

    private val _uiState = MutableStateFlow<HabitsUiState>(HabitsUiState.Loading)
    val uiState: StateFlow<HabitsUiState> = _uiState

    init {
        loadHabits()
    }

    fun loadHabits() {
        _uiState.value = HabitsUiState.Loading
        viewModelScope.launch {
            try {
                val habits = api.getHabits(_dateStr.value)
                _uiState.value = HabitsUiState.Success(habits)
            } catch (e: Exception) {
                _uiState.value = HabitsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun previousDay() { calendar.add(Calendar.DAY_OF_YEAR, -1); syncDate() }
    fun nextDay()     { calendar.add(Calendar.DAY_OF_YEAR, +1); syncDate() }
    fun returnToday() { calendar.time = Date();                  syncDate() }

    private fun syncDate() {
        _dateStr.value     = dateFormat.format(calendar.time)
        _displayDate.value = displayFmt.format(calendar.time)
        _isToday.value     = _dateStr.value == todayStr
        loadHabits()
    }

    private fun applyOptimistic(habitId: Int, newValue: Double) {
        val s = _uiState.value as? HabitsUiState.Success ?: return
        _uiState.value = s.copy(
            habits = s.habits.map { if (it.id == habitId) it.copy(logValue = newValue) else it }
        )
    }

    fun incrementHabit(habit: Habit) {
        val new = (habit.logValue ?: 0.0) + 1.0
        applyOptimistic(habit.id, new)
        viewModelScope.launch {
            try {
                api.logHabit(LogRequest(habit.id, _dateStr.value, new))
                api.getHabits(_dateStr.value).let { _uiState.value = HabitsUiState.Success(it) }
            } catch (e: Exception) {
                loadHabits()
            }
        }
    }

    fun decrementHabit(habit: Habit) {
        val new = (habit.logValue ?: 0.0) - 1.0
        applyOptimistic(habit.id, new)
        viewModelScope.launch {
            try {
                api.logHabit(LogRequest(habit.id, _dateStr.value, new))
                api.getHabits(_dateStr.value).let { _uiState.value = HabitsUiState.Success(it) }
            } catch (e: Exception) {
                loadHabits()
            }
        }
    }

    fun setHabitValue(habit: Habit, value: Double) {
        applyOptimistic(habit.id, value)
        viewModelScope.launch {
            try {
                api.logHabit(LogRequest(habit.id, _dateStr.value, value))
                api.getHabits(_dateStr.value).let { _uiState.value = HabitsUiState.Success(it) }
            } catch (e: Exception) {
                loadHabits()
            }
        }
    }
}
