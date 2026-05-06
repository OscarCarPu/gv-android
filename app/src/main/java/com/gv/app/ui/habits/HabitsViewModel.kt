package com.gv.app.ui.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class HabitsUiState {
    object Loading : HabitsUiState()
    data class Loaded(val habits: List<HabitWithLog>) : HabitsUiState()
    data class Error(val message: String) : HabitsUiState()
}

class HabitsViewModel(app: Application) : AndroidViewModel(app) {

    private val api: ApiService = RetrofitClient.apiService

    private val _state = MutableStateFlow<HabitsUiState>(HabitsUiState.Loading)
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val pendingLogJobs = mutableMapOf<Int, Job>()

    init {
        refresh()
    }

    fun refresh() {
        val date = _selectedDate.value
        _state.value = HabitsUiState.Loading
        viewModelScope.launch {
            fetchFor(date)
        }
    }

    fun onPrevDay() = onDateChange(_selectedDate.value.minusDays(1))
    fun onNextDay() = onDateChange(_selectedDate.value.plusDays(1))
    fun onToday() = onDateChange(LocalDate.now())

    fun onDateChange(date: LocalDate) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        pendingLogJobs.values.forEach { it.cancel() }
        pendingLogJobs.clear()
        _state.value = HabitsUiState.Loading
        viewModelScope.launch { fetchFor(date) }
    }

    fun onAdjust(habitId: Int, delta: Double) {
        val loaded = _state.value as? HabitsUiState.Loaded ?: return
        val current = loaded.habits.firstOrNull { it.id == habitId } ?: return
        val newValue = (current.log_value ?: 0.0) + delta
        applyOptimistic(habitId, newValue)
        scheduleLog(habitId, newValue)
    }

    fun onSetValue(habitId: Int, value: Double) {
        applyOptimistic(habitId, value)
        scheduleLog(habitId, value)
    }

    fun onDelete(habitId: Int) {
        val loaded = _state.value as? HabitsUiState.Loaded ?: return
        val previous = loaded.habits
        _state.value = HabitsUiState.Loaded(previous.filterNot { it.id == habitId })
        pendingLogJobs.remove(habitId)?.cancel()
        viewModelScope.launch {
            try {
                val response = api.deleteHabit(habitId)
                if (!response.isSuccessful) {
                    _state.value = HabitsUiState.Loaded(previous)
                    _toast.emit("Failed to delete habit")
                }
            } catch (e: Exception) {
                _state.value = HabitsUiState.Loaded(previous)
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    private fun applyOptimistic(habitId: Int, newValue: Double) {
        val loaded = _state.value as? HabitsUiState.Loaded ?: return
        val updated = loaded.habits.map {
            if (it.id == habitId) it.copy(log_value = newValue) else it
        }
        _state.value = HabitsUiState.Loaded(updated)
    }

    private fun scheduleLog(habitId: Int, value: Double) {
        val date = _selectedDate.value
        pendingLogJobs[habitId]?.cancel()
        pendingLogJobs[habitId] = viewModelScope.launch {
            delay(300)
            try {
                val response = api.logHabit(
                    LogHabitRequest(
                        habit_id = habitId,
                        date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        value = value,
                    ),
                )
                if (response.isSuccessful) {
                    fetchFor(date, replaceState = false)
                } else {
                    _toast.emit("Failed to save")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            } finally {
                pendingLogJobs.remove(habitId)
            }
        }
    }

    private suspend fun fetchFor(date: LocalDate, replaceState: Boolean = true) {
        try {
            val response = api.getHabits(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            if (response.isSuccessful) {
                _state.value = HabitsUiState.Loaded(response.body() ?: emptyList())
            } else if (replaceState) {
                _state.value = HabitsUiState.Error("Failed to load habits")
            }
        } catch (e: Exception) {
            if (replaceState) {
                _state.value = HabitsUiState.Error(e.message ?: "Network error")
            }
        }
    }
}
