package com.gv.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class TimerUiState(
    val activeEntry: TimeEntry? = null,
    val isRunning: Boolean = false,
    val elapsedSeconds: Long = 0,
    val selectedTaskId: Int? = null,
    val selectedTaskName: String = "",
    val comment: String = "",
    val summary: TimeEntrySummary? = null,
    val tasksByDueDate: List<TaskByDueDate> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class TimerViewModel(
    private val api: ApiService = RetrofitClient.apiService
) : ViewModel() {

    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state

    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            // Load tasks first, then resolve active entry (needs task names)
            try {
                val tasks = api.getTasksByDueDate()
                _state.update { it.copy(tasksByDueDate = tasks) }
            } catch (_: Exception) {}
            resolveActiveEntry()
        }
        loadSummary()
    }

    fun loadActiveEntry() {
        viewModelScope.launch { resolveActiveEntry() }
    }

    private suspend fun resolveActiveEntry() {
        try {
            val entry = api.getActiveTimeEntry()
            if (entry != null) {
                val startInstant = parseInstant(entry.startedAt)
                val elapsed = ChronoUnit.SECONDS.between(startInstant, Instant.now())
                val taskName = try { api.getTask(entry.taskId).name } catch (_: Exception) { "" }
                _state.update {
                    it.copy(
                        activeEntry = entry,
                        isRunning = true,
                        elapsedSeconds = elapsed.coerceAtLeast(0),
                        selectedTaskId = entry.taskId,
                        selectedTaskName = taskName,
                        comment = entry.comment ?: "",
                        isLoading = false
                    )
                }
                startTicker(startInstant)
            } else {
                _state.update { it.copy(isRunning = false, isLoading = false) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    fun loadSummary() {
        viewModelScope.launch {
            try {
                val summary = api.getTimeEntrySummary()
                _state.update { it.copy(summary = summary) }
            } catch (_: Exception) {}
        }
    }

    fun loadTasks() {
        viewModelScope.launch {
            try {
                val tasks = api.getTasksByDueDate()
                _state.update { it.copy(tasksByDueDate = tasks) }
            } catch (_: Exception) {}
        }
    }

    fun selectTask(taskId: Int, taskName: String) {
        _state.update { it.copy(selectedTaskId = taskId, selectedTaskName = taskName) }
    }

    fun updateComment(comment: String) {
        _state.update { it.copy(comment = comment) }
        val entry = _state.value.activeEntry ?: return
        viewModelScope.launch {
            try {
                api.updateTimeEntry(entry.id, UpdateTimeEntryRequest(comment = comment.ifBlank { null }))
            } catch (_: Exception) {}
        }
    }

    fun startTimer() {
        val taskId = _state.value.selectedTaskId ?: return
        val current = _state.value.activeEntry

        viewModelScope.launch {
            try {
                if (current != null && _state.value.isRunning) {
                    // Timer already running — just reassign to the new task
                    val updated = api.updateTimeEntry(
                        current.id,
                        UpdateTimeEntryRequest(taskId = taskId)
                    )
                    _state.update { it.copy(activeEntry = updated) }
                } else {
                    // No active timer — create a new entry
                    val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    val entry = api.createTimeEntry(
                        CreateTimeEntryRequest(
                            taskId = taskId,
                            startedAt = now,
                            comment = _state.value.comment.ifBlank { null }
                        )
                    )
                    val startInstant = parseInstant(entry.startedAt)
                    _state.update {
                        it.copy(activeEntry = entry, isRunning = true, elapsedSeconds = 0)
                    }
                    startTicker(startInstant)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun stopTimer() {
        val entry = _state.value.activeEntry ?: return
        tickerJob?.cancel()
        viewModelScope.launch {
            try {
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                api.updateTimeEntry(
                    entry.id,
                    UpdateTimeEntryRequest(
                        finishedAt = now,
                        comment = _state.value.comment.ifBlank { null }
                    )
                )
                _state.update {
                    it.copy(
                        activeEntry = null,
                        isRunning = false,
                        elapsedSeconds = 0,
                        comment = ""
                    )
                }
                loadSummary()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun addManualEntry(taskId: Int, startedAt: String, finishedAt: String, comment: String?) {
        viewModelScope.launch {
            try {
                api.createTimeEntry(
                    CreateTimeEntryRequest(
                        taskId = taskId,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        comment = comment
                    )
                )
                loadSummary()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun parseInstant(value: String): Instant {
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            ZonedDateTime.parse(value).toInstant()
        }
    }

    private fun startTicker(startInstant: Instant) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val elapsed = ChronoUnit.SECONDS.between(startInstant, Instant.now())
                _state.update { it.copy(elapsedSeconds = elapsed.coerceAtLeast(0)) }
                delay(1000)
            }
        }
    }
}
