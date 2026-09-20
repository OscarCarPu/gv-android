package com.gv.app.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.TaskRepository
import com.gv.app.data.repository.TasksData
import com.gv.app.domain.model.ActiveTimer
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.TaskFastResponse
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

sealed class TasksUiState {
    data object Loading : TasksUiState()

    /** [due] and [tree] are [data] with the board's filters and pending finishes applied. */
    data class Loaded(
        val data: TasksData,
        val due: DueSoonView,
        val tree: List<ActiveTreeNode>,
        val filters: BoardFilters,
    ) : TasksUiState()
}

/** The timer picker's list, which is read live each time it opens. */
sealed interface PickerTasks {
    data object Loading : PickerTasks
    data class Loaded(val tasks: List<TaskFastResponse>) : PickerTasks
    data object Failed : PickerTasks
}

data class TimerState(
    val active: ActiveTimer?,
    val elapsedSeconds: Long,
) {
    val isRunning: Boolean get() = active != null
}

/**
 * The Tasks screen's ViewModel. Online-first, offline read-only: lists are collected from the
 * Room snapshot (instant, and still there with no connection) and re-read after every write;
 * the timer ticks locally from the server-issued start time. Every write goes through
 * [TaskRepository], which refuses it when offline, and each failure comes back on [toast].
 *
 * The plan has its own [PlanViewModel]; this one owns everything else on the screen.
 */
class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: TaskRepository = app.container.taskRepository

    private val _refreshing = MutableStateFlow(true)
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _editingDetail = MutableStateFlow<TaskFullResponse?>(null)
    val editingDetail: StateFlow<TaskFullResponse?> = _editingDetail.asStateFlow()

    private val filters = MutableStateFlow(BoardFilters())

    // Finished a moment ago and hidden until the re-read confirms it. The repository never
    // patches its own cache from a request, so this is the only optimism the lists have.
    private val pendingTasks = MutableStateFlow<Set<Int>>(emptySet())
    private val pendingProjects = MutableStateFlow<Set<Int>>(emptySet())

    val state: StateFlow<TasksUiState> =
        combine(repo.tasksData(), _refreshing, filters, pendingTasks, pendingProjects) { data, refreshing, f, pt, pp ->
            when {
                data != null -> loaded(data, f, pt, pp)
                refreshing -> TasksUiState.Loading
                else -> loaded(EMPTY_DATA, f, pt, pp)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState.Loading)

    private fun loaded(data: TasksData, f: BoardFilters, pt: Set<Int>, pp: Set<Int>) = TasksUiState.Loaded(
        data = data,
        due = buildDueSoonView(data.byDueDate, data.tree, f, pt),
        tree = filterTree(data.tree, f.treePriority, pt, pp),
        filters = f,
    )

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

    // ----- Board filters -----

    fun setDuePriority(value: Int?) = filters.update { it.withDuePriority(value) }

    fun setDueProject(value: Int?) = filters.update { it.withDueProject(value) }

    fun setTreePriority(value: Int?) = filters.update { it.withTreePriority(value) }

    fun showMoreDue() = filters.update { it.copy(dueVisibleCount = it.dueVisibleCount + DUE_EXPAND_STEP) }

    // ----- Timer -----

    /**
     * Only the task id is needed now: the server creates the entry and hands back its name,
     * project and type, so the caller no longer has to pass what it thinks those are.
     */
    fun startOrAssignTimer(taskId: Int) {
        viewModelScope.launch { report(repo.startOrAssignTimer(taskId)) }
    }

    /** Finish the running timer and begin one on [taskId] in a single tap (the row's "Stop Start"). */
    fun stopAndStartTimer(taskId: Int) {
        viewModelScope.launch { report(repo.stopAndStartTimer(taskId)) }
    }

    /** Read live on each open; the caller decides how to say "could not load". */
    suspend fun pickerTasks(): PickerTasks = repo.pickerTasks().toPickerTasks()

    fun stopTimer(comment: String?) {
        viewModelScope.launch { report(repo.stopTimer(comment)) }
    }

    fun cancelTimer() {
        viewModelScope.launch { report(repo.cancelTimer()) }
    }

    fun updateTimerComment(comment: String) {
        viewModelScope.launch { report(repo.updateTimerComment(comment)) }
    }

    /**
     * Surfaces a write's failure. Offline refusals land here too: the banner explains the
     * state, but a button that silently does nothing reads as a bug, so it gets said out loud.
     */
    private suspend fun report(result: ApiResult<*>) {
        if (result is ApiResult.Failure) _toast.emit(result.message)
    }

    // ----- Task mutations -----

    fun startTask(taskId: Int) {
        viewModelScope.launch { report(repo.startTask(taskId)) }
    }

    /**
     * Done, or Renew for a recurring task. A finished task disappears at once; a renewed one
     * stays (only its date moves), so it is never hidden.
     */
    fun finishOrRenew(taskId: Int, taskType: String?, recurrence: Int?) {
        viewModelScope.launch {
            val renews = taskType == "recurring" && recurrence != null
            if (!renews) pendingTasks.update { it + taskId }
            val result = repo.finishOrRenew(taskId, taskType, recurrence)
            pendingTasks.update { it - taskId }
            report(result)
        }
    }

    /** Start / finish / renew a node in the Projects tree. */
    fun toggleTreeNode(id: Int, type: String, finish: Boolean) {
        viewModelScope.launch {
            if (type == "project") {
                if (!finish) return@launch report(repo.startProject(id))
                pendingProjects.update { it + id }
                val result = repo.finishProject(id)
                pendingProjects.update { it - id }
                report(result)
                return@launch
            }
            if (!finish) return@launch report(repo.startTask(id))
            val node = (state.value as? TasksUiState.Loaded)?.data?.tree?.let { findTreeTask(it, id) }
            finishOrRenew(id, node?.task_type, node?.recurrence)
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
            val result = repo.updateTaskDetail(id, name, description, dueAt, taskType, recurrence, priority)
            report(result)
            onDone(result is ApiResult.Success)
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            if (_editingDetail.value?.id == id) _editingDetail.value = null
            report(repo.deleteTask(id))
        }
    }

    fun createTask(req: CreateTaskRequest, startNow: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.createTask(req, startNow)
            report(result)
            onDone(result is ApiResult.Success)
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
        viewModelScope.launch { report(repo.editActiveTimerStart(startedAtIso)) }
    }

    /** [onDone] runs once the write has finished, success or not, so the caller can re-read. */
    fun createPastEntry(
        taskId: Int,
        startedAtIso: String,
        finishedAtIso: String,
        comment: String?,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            report(repo.createPastEntry(taskId, startedAtIso, finishedAtIso, comment))
            onDone()
        }
    }

    fun editEntry(
        id: Int,
        taskId: Int?,
        startedAtIso: String?,
        finishedAtIso: String?,
        comment: String?,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            report(repo.editEntry(id, taskId, startedAtIso, finishedAtIso, comment))
            onDone()
        }
    }

    fun deleteEntry(id: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            report(repo.deleteEntry(id))
            onDone()
        }
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

/** A failed load must read as "could not load", never as an empty account. */
fun ApiResult<List<TaskFastResponse>>.toPickerTasks(): PickerTasks = when (this) {
    is ApiResult.Success -> PickerTasks.Loaded(data)
    is ApiResult.Failure -> PickerTasks.Failed
}
