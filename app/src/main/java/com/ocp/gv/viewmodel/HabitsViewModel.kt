package com.ocp.gv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocp.gv.data.RetrofitClient
import com.ocp.gv.data.mappers.toDomain
import com.ocp.gv.data.models.Habit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HabitsViewModel : ViewModel() {
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadHabits()
    }

    fun loadHabits() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = RetrofitClient.habitApi.getHabits()
                _habits.value = response.items.map { it.toDomain() }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
