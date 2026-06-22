package com.gv.app.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.TaskRepository
import com.gv.app.data.repository.TasksData
import com.gv.app.domain.model.ActiveTimer
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.TaskFullResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

sealed class TasksUiState {
    data object Loading : TasksUiState()
    data class Loaded(val data: TasksData) : TasksUiState()
}

data class TimerState(
    val active: ActiveTimer?,
    val elapsedSeconds: Long,
) {
    val isRunning: Boolean get() = active != null
}

/**
 * Offline-first tasks ViewModel. The list is collected from the Room snapshot (instant, works
 * offline) and reconciled in the background; the timer ticks locally from the cached start time.
 * All mutations go through [TaskRepository], which commits locally / queues sync so actions
 * don't block on the network.
 */
class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: TaskRepository = app.container.taskRepository

    private val _refreshing = MutableStateFlow(true)
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _editingDetail = MutableStateFlow<TaskFullResponse?>(null)
    val editingDetail: StateFlow<TaskFullResponse?> = _editingDetail.asStateFlow()

    val state: StateFlow<TasksUiState> =
        combine(repo.tasksData(), _refreshing) { data, refreshing ->
            when {
                data != null -> TasksUiState.Loaded(data)
                refreshing -> TasksUiState.Loading
                else -> TasksUiState.Loaded(EMPTY_DATA)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val timer: StateFlow<TimerState> =
        repo.activeTimer().flatMapLatest { active ->
            if (active == null) {
                flowOf(TimerState(null, 0L))
            } else {
                flow {
                    while (true) {
                        emit(TimerState(active, elapsedFor(active.startedAt)))
                        delay(1_000)
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerState(null, 0L))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val result = repo.reconcile()
            _refreshing.value = false
            if (result is ApiResult.Failure && result.code != null && result.code in 400..599) {
                _toast.emit(result.message)
            }
        }
    }

    // ----- Timer -----

    fun startOrAssignTimer(
        taskId: Int,
        taskName: String,
        projectName: String?,
        taskType: String?,
        recurrence: Int?,
        priority: Int?,
    ) {
        viewModelScope.launch {
            repo.startOrAssignTimer(taskId, taskName, projectName, taskType, recurrence, priority)
        }
    }

    fun stopTimer(comment: String?) {
        viewModelScope.launch { repo.stopTimer(comment) }
    }

    fun cancelTimer() {
        viewModelScope.launch { repo.cancelTimer() }
    }

    fun updateTimerComment(comment: String) {
        viewModelScope.launch { repo.updateTimerComment(comment) }
    }

    // ----- Task mutations -----

    fun startTask(taskId: Int) {
        viewModelScope.launch { repo.startTask(taskId) }
    }

    fun finishOrRenew(taskId: Int, taskType: String?, recurrence: Int?) {
        viewModelScope.launch { repo.finishOrRenew(taskId, taskType, recurrence) }
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
            repo.updateTaskDetail(id, name, description, dueAt, taskType, recurrence, priority)
            onDone(true)
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            if (_editingDetail.value?.id == id) _editingDetail.value = null
            repo.deleteTask(id)
        }
    }

    fun createTask(req: CreateTaskRequest, startNow: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repo.createTask(req, startNow)
            onDone(true)
        }
    }

    // ----- Detail + todos (network-dependent drill-in) -----

    fun openDetail(taskId: Int) {
        viewModelScope.launch { loadDetail(taskId) }
    }

    fun closeDetail() {
        _editingDetail.value = null
    }

    private suspend fun loadDetail(taskId: Int) {
        when (val r = repo.loadTaskDetail(taskId)) {
            is ApiResult.Success -> _editingDetail.value = r.data
            is ApiResult.Failure -> _toast.emit(r.message)
        }
    }

    fun addTodo(taskId: Int, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (repo.addTodo(taskId, name)) {
                is ApiResult.Success -> loadDetail(taskId)
                is ApiResult.Failure -> _toast.emit("Failed to add todo")
            }
        }
    }

    fun toggleTodo(taskId: Int, todoId: Int, isDone: Boolean) {
        viewModelScope.launch {
            when (repo.toggleTodo(todoId, isDone)) {
                is ApiResult.Success -> loadDetail(taskId)
                is ApiResult.Failure -> _toast.emit("Failed to update todo")
            }
        }
    }

    fun deleteTodo(taskId: Int, todoId: Int) {
        viewModelScope.launch {
            when (repo.deleteTodo(todoId)) {
                is ApiResult.Success -> loadDetail(taskId)
                is ApiResult.Failure -> _toast.emit("Failed to delete todo")
            }
        }
    }

    // ----- Time-entry editing / agenda -----

    fun loadDayEntries(date: java.time.LocalDate, onResult: (List<com.gv.app.domain.model.TimeEntryWithTaskResponse>?) -> Unit) {
        viewModelScope.launch {
            onResult((repo.loadDayEntries(date) as? ApiResult.Success)?.data)
        }
    }

    fun loadTaskOptions(onResult: (List<com.gv.app.domain.model.TaskOption>) -> Unit) {
        viewModelScope.launch { onResult(repo.taskOptions()) }
    }

    fun editActiveTimerStart(startedAtIso: String) {
        viewModelScope.launch { repo.editActiveTimerStart(startedAtIso) }
    }

    fun createPastEntry(taskId: Int, startedAtIso: String, finishedAtIso: String, comment: String?) {
        viewModelScope.launch { repo.createPastEntry(taskId, startedAtIso, finishedAtIso, comment) }
    }

    fun editEntry(id: Int, taskId: Int?, startedAtIso: String?, finishedAtIso: String?, comment: String?) {
        viewModelScope.launch { repo.editEntry(id, taskId, startedAtIso, finishedAtIso, comment) }
    }

    fun deleteEntry(id: Int) {
        viewModelScope.launch { repo.deleteEntry(id) }
    }

    private fun elapsedFor(startedAt: String?): Long {
        if (startedAt.isNullOrBlank()) return 0L
        return try {
            (Instant.now().epochSecond - Instant.parse(startedAt).epochSecond).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    private companion object {
        val EMPTY_DATA = TasksData(emptyList(), emptyList(), null, null, emptyList())
    }
}
