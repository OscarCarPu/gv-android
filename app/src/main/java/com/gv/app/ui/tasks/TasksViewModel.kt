package com.gv.app.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.domain.model.ActiveTimeEntryResponse
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.CreateTimeEntryRequest
import com.gv.app.domain.model.CreateTodoRequest
import com.gv.app.domain.model.PlanTodayResponse
import com.gv.app.domain.model.ProjectListItem
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.domain.model.TaskFullResponse
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.domain.model.UpdateTaskRequest
import com.gv.app.domain.model.UpdateTimeEntryRequest
import com.gv.app.domain.model.UpdateTodoRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class TasksData(
    val byDueDate: List<TaskByDueDateResponse>,
    val tree: List<ActiveTreeNode>,
    val summary: TimeEntrySummaryResponse,
    val plan: PlanTodayResponse?,
    val projects: List<ProjectListItem>,
)

sealed class TasksUiState {
    data object Loading : TasksUiState()
    data class Loaded(val data: TasksData) : TasksUiState()
    data class Error(val message: String) : TasksUiState()
}

data class TimerState(
    val active: ActiveTimeEntryResponse?,
    val elapsedSeconds: Long,
) {
    val isRunning: Boolean get() = active != null
}

class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val api: ApiService = RetrofitClient.apiService

    private val _state = MutableStateFlow<TasksUiState>(TasksUiState.Loading)
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _timer = MutableStateFlow(TimerState(active = null, elapsedSeconds = 0L))
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _editingDetail = MutableStateFlow<TaskFullResponse?>(null)
    val editingDetail: StateFlow<TaskFullResponse?> = _editingDetail.asStateFlow()

    private var tickJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is TasksUiState.Loaded) {
                _state.value = TasksUiState.Loading
            }
            try {
                val data = loadAll()
                _state.value = TasksUiState.Loaded(data)
            } catch (e: Exception) {
                if (_state.value !is TasksUiState.Loaded) {
                    _state.value = TasksUiState.Error(e.message ?: "Network error")
                } else {
                    _toast.emit(e.message ?: "Network error")
                }
            }
            refreshActiveTimer()
        }
    }

    private suspend fun loadAll(): TasksData {
        return kotlinx.coroutines.coroutineScope {
            val dueDef = async {
                val r = api.getTasksByDueDate()
                if (!r.isSuccessful) error("Failed to load tasks by due date")
                r.body() ?: emptyList()
            }
            val treeDef = async {
                val r = api.getActiveTree()
                if (!r.isSuccessful) error("Failed to load active tree")
                r.body() ?: emptyList()
            }
            val summaryDef = async {
                val r = api.getTimeEntrySummary()
                if (!r.isSuccessful) error("Failed to load summary")
                r.body()!!
            }
            val planDef = async {
                runCatching {
                    val r = api.getPlanToday()
                    if (r.isSuccessful) r.body() else null
                }.getOrNull()
            }
            val projectsDef = async {
                runCatching {
                    val r = api.listProjectsFast()
                    if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
                }.getOrDefault(emptyList())
            }
            TasksData(
                byDueDate = dueDef.await(),
                tree = treeDef.await(),
                summary = summaryDef.await(),
                plan = planDef.await(),
                projects = projectsDef.await(),
            )
        }
    }

    // ----- Timer -----

    private suspend fun refreshActiveTimer() {
        try {
            val r = api.getActiveTimeEntry()
            if (r.isSuccessful) {
                val active = r.body()
                _timer.value = TimerState(
                    active = active,
                    elapsedSeconds = elapsedFor(active?.started_at),
                )
                if (active != null) startTick() else stopTick()
            } else {
                _timer.value = TimerState(active = null, elapsedSeconds = 0L)
                stopTick()
            }
        } catch (_: Exception) {
            // network blip: keep existing state
        }
    }

    private fun startTick() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                val active = _timer.value.active
                if (active == null) break
                _timer.value = _timer.value.copy(elapsedSeconds = elapsedFor(active.started_at))
                delay(1000)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun elapsedFor(startedAt: String?): Long {
        if (startedAt.isNullOrBlank()) return 0L
        return try {
            val start = Instant.parse(startedAt).epochSecond
            val now = Instant.now().epochSecond
            (now - start).coerceAtLeast(0L)
        } catch (_: Exception) { 0L }
    }

    fun startOrAssignTimer(taskId: Int) {
        viewModelScope.launch {
            try {
                val active = _timer.value.active
                if (active == null) {
                    val r = api.createTimeEntry(
                        CreateTimeEntryRequest(
                            task_id = taskId,
                            started_at = nowIsoUtc(),
                            finished_at = null,
                            comment = null,
                        ),
                    )
                    if (!r.isSuccessful) {
                        _toast.emit("Failed to start timer")
                        return@launch
                    }
                } else {
                    val r = api.updateTimeEntry(active.id, UpdateTimeEntryRequest(
                        task_id = taskId,
                        started_at = null,
                        finished_at = null,
                        comment = null,
                    ))
                    if (!r.isSuccessful) {
                        _toast.emit("Failed to reassign timer")
                        return@launch
                    }
                }
                refreshActiveTimer()
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun stopTimer(comment: String?) {
        viewModelScope.launch {
            val active = _timer.value.active ?: return@launch
            try {
                val r = api.updateTimeEntry(active.id, UpdateTimeEntryRequest(
                    task_id = null,
                    started_at = null,
                    finished_at = nowIsoUtc(),
                    comment = comment?.takeIf { it.isNotBlank() },
                ))
                if (!r.isSuccessful) {
                    _toast.emit("Failed to stop timer")
                    return@launch
                }
                _timer.value = TimerState(active = null, elapsedSeconds = 0L)
                stopTick()
                refresh()
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun cancelTimer() {
        viewModelScope.launch {
            val active = _timer.value.active ?: return@launch
            try {
                val r = api.deleteTimeEntry(active.id)
                if (!r.isSuccessful) {
                    _toast.emit("Failed to cancel timer")
                    return@launch
                }
                _timer.value = TimerState(active = null, elapsedSeconds = 0L)
                stopTick()
                refresh()
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun updateTimerComment(comment: String) {
        val active = _timer.value.active ?: return
        viewModelScope.launch {
            try {
                api.updateTimeEntry(active.id, UpdateTimeEntryRequest(
                    task_id = null,
                    started_at = null,
                    finished_at = null,
                    comment = comment.ifBlank { null },
                ))
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    // ----- Task mutations -----

    fun startTask(taskId: Int) {
        mutateTaskField(taskId, started_at = nowIsoUtc())
    }

    fun finishTaskOrRenew(task: TaskByDueDateResponse) {
        if (task.task_type == "recurring" && task.recurrence != null) {
            mutateTaskField(task.id, due_at = buildRecurringDueAt(task.recurrence))
            return
        }
        mutateTaskField(task.id, finished_at = nowIsoUtc())
    }

    private fun mutateTaskField(
        id: Int,
        name: String? = null,
        description: String? = null,
        due_at: String? = null,
        project_id: Int? = null,
        started_at: String? = null,
        finished_at: String? = null,
        task_type: String? = null,
        recurrence: Int? = null,
        priority: Int? = null,
    ) {
        viewModelScope.launch {
            try {
                val r = api.updateTask(
                    id,
                    UpdateTaskRequest(
                        name = name,
                        description = description,
                        due_at = due_at,
                        project_id = project_id,
                        started_at = started_at,
                        finished_at = finished_at,
                        task_type = task_type,
                        recurrence = recurrence,
                        priority = priority,
                    ),
                )
                if (r.isSuccessful) {
                    refresh()
                    if (_editingDetail.value?.id == id) loadDetail(id, replaceState = true)
                } else {
                    _toast.emit("Failed to update task")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun saveTaskDetail(
        id: Int,
        name: String,
        description: String?,
        dueAt: String?,
        taskType: String,
        recurrence: Int?,
        priority: Int,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val r = api.updateTask(
                    id,
                    UpdateTaskRequest(
                        name = name,
                        description = description,
                        due_at = dueAt,
                        project_id = null,
                        started_at = null,
                        finished_at = null,
                        task_type = taskType,
                        recurrence = if (taskType == "recurring") recurrence else null,
                        priority = priority,
                    ),
                )
                if (r.isSuccessful) {
                    onDone(true)
                    refresh()
                    loadDetail(id, replaceState = true)
                } else {
                    _toast.emit("Failed to save task")
                    onDone(false)
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
                onDone(false)
            }
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            try {
                val r = api.deleteTask(id)
                if (r.isSuccessful) {
                    if (_editingDetail.value?.id == id) _editingDetail.value = null
                    refresh()
                } else {
                    _toast.emit("Failed to delete task")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun createTask(req: CreateTaskRequest, startNow: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = api.createTask(req)
                if (r.isSuccessful) {
                    val created = r.body()
                    onDone(true)
                    if (startNow && created != null) {
                        api.updateTask(created.id, UpdateTaskRequest(
                            name = null, description = null, due_at = null,
                            project_id = null, started_at = nowIsoUtc(),
                            finished_at = null, task_type = null, recurrence = null, priority = null,
                        ))
                    }
                    refresh()
                } else {
                    _toast.emit("Failed to create task")
                    onDone(false)
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
                onDone(false)
            }
        }
    }

    // ----- Task detail (load + todos) -----

    fun openDetail(taskId: Int) {
        loadDetail(taskId, replaceState = true)
    }

    fun closeDetail() {
        _editingDetail.value = null
    }

    private fun loadDetail(taskId: Int, replaceState: Boolean) {
        viewModelScope.launch {
            try {
                val r = api.getTask(taskId)
                if (r.isSuccessful) {
                    if (replaceState || _editingDetail.value?.id == taskId) {
                        _editingDetail.value = r.body()
                    }
                } else {
                    _toast.emit("Failed to load task")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun addTodo(taskId: Int, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val r = api.createTodo(CreateTodoRequest(taskId, name.trim()))
                if (r.isSuccessful) loadDetail(taskId, replaceState = true)
                else _toast.emit("Failed to add todo")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun toggleTodo(taskId: Int, todoId: Int, isDone: Boolean) {
        viewModelScope.launch {
            try {
                val r = api.updateTodo(todoId, UpdateTodoRequest(name = null, is_done = isDone))
                if (r.isSuccessful) loadDetail(taskId, replaceState = true)
                else _toast.emit("Failed to update todo")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    fun deleteTodo(taskId: Int, todoId: Int) {
        viewModelScope.launch {
            try {
                val r = api.deleteTodo(todoId)
                if (r.isSuccessful) loadDetail(taskId, replaceState = true)
                else _toast.emit("Failed to delete todo")
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTick()
    }
}
