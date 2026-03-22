package com.gv.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class CreateMode { TASK, PROJECT }

data class TasksUiState(
    val tasksByDueDate: List<TaskByDueDate> = emptyList(),
    val activeTree: List<ActiveTreeNode> = emptyList(),
    val expandedNodeIds: Set<Int> = emptySet(),
    val projectsFast: List<ProjectFast> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // Bottom sheet state
    val selectedTask: TaskFull? = null,
    val selectedProject: ProjectDetail? = null,
    val showCreateSheet: Boolean = false,
    val createMode: CreateMode = CreateMode.TASK
)

class TasksViewModel(
    private val api: ApiService = RetrofitClient.apiService
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state

    init {
        loadAll()
    }

    fun loadAll() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val tasks = api.getTasksByDueDate()
                val tree = api.getActiveTree()
                val projects = api.getProjectsFast()
                _state.update {
                    it.copy(
                        tasksByDueDate = tasks,
                        activeTree = tree,
                        projectsFast = projects,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Tree expansion ──────────────────────────────────

    fun toggleNodeExpanded(nodeId: Int) {
        _state.update {
            val ids = it.expandedNodeIds.toMutableSet()
            if (nodeId in ids) ids.remove(nodeId) else ids.add(nodeId)
            it.copy(expandedNodeIds = ids)
        }
    }

    // ── Task detail ─────────────────────────────────────

    fun loadTaskDetail(taskId: Int) {
        viewModelScope.launch {
            try {
                val task = api.getTask(taskId)
                _state.update { it.copy(selectedTask = task) }
            } catch (_: Exception) {}
        }
    }

    fun dismissTaskDetail() {
        _state.update { it.copy(selectedTask = null) }
    }

    // ── Project detail ──────────────────────────────────

    fun loadProjectDetail(projectId: Int) {
        viewModelScope.launch {
            try {
                val project = api.getProject(projectId)
                _state.update { it.copy(selectedProject = project) }
            } catch (_: Exception) {}
        }
    }

    fun dismissProjectDetail() {
        _state.update { it.copy(selectedProject = null) }
    }

    // ── Create sheet ────────────────────────────────────

    fun showCreate(mode: CreateMode) {
        _state.update { it.copy(showCreateSheet = true, createMode = mode) }
    }

    fun dismissCreate() {
        _state.update { it.copy(showCreateSheet = false) }
    }

    // ── Task CRUD ───────────────────────────────────────

    fun createTask(name: String, description: String?, dueAt: String?, projectId: Int?) {
        viewModelScope.launch {
            try {
                api.createTask(CreateTaskRequest(projectId = projectId, name = name, description = description, dueAt = dueAt))
                _state.update { it.copy(showCreateSheet = false) }
                loadAll()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun startTask(taskId: Int) {
        viewModelScope.launch {
            try {
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                api.updateTask(taskId, UpdateTaskRequest(startedAt = now))
                loadAll()
                // Refresh detail if open
                if (_state.value.selectedTask?.id == taskId) loadTaskDetail(taskId)
            } catch (_: Exception) {}
        }
    }

    fun finishTask(taskId: Int) {
        viewModelScope.launch {
            try {
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                api.updateTask(taskId, UpdateTaskRequest(finishedAt = now))
                loadAll()
                dismissTaskDetail()
            } catch (_: Exception) {}
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            try {
                api.deleteTask(taskId)
                dismissTaskDetail()
                loadAll()
            } catch (_: Exception) {}
        }
    }

    // ── Project CRUD ────────────────────────────────────

    fun createProject(name: String, description: String?, dueAt: String?, parentId: Int?) {
        viewModelScope.launch {
            try {
                api.createProject(CreateProjectRequest(name = name, description = description, dueAt = dueAt, parentId = parentId))
                _state.update { it.copy(showCreateSheet = false) }
                loadAll()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun startProject(projectId: Int) {
        viewModelScope.launch {
            try {
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                api.updateProject(projectId, UpdateProjectRequest(startedAt = now))
                loadAll()
                if (_state.value.selectedProject?.id == projectId) loadProjectDetail(projectId)
            } catch (_: Exception) {}
        }
    }

    fun finishProject(projectId: Int) {
        viewModelScope.launch {
            try {
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                api.updateProject(projectId, UpdateProjectRequest(finishedAt = now))
                loadAll()
                dismissProjectDetail()
            } catch (_: Exception) {}
        }
    }

    fun deleteProject(projectId: Int) {
        viewModelScope.launch {
            try {
                api.deleteProject(projectId)
                dismissProjectDetail()
                loadAll()
            } catch (_: Exception) {}
        }
    }

    // ── Todos ───────────────────────────────────────────

    fun createTodo(taskId: Int, name: String) {
        viewModelScope.launch {
            try {
                api.createTodo(CreateTodoRequest(taskId = taskId, name = name))
                loadTaskDetail(taskId)
            } catch (_: Exception) {}
        }
    }

    fun toggleTodo(todo: Todo) {
        // Optimistic
        _state.update { s ->
            val task = s.selectedTask ?: return@update s
            s.copy(selectedTask = task.copy(
                todos = task.todos.map { if (it.id == todo.id) it.copy(isDone = !todo.isDone) else it }
            ))
        }
        viewModelScope.launch {
            try {
                api.updateTodo(todo.id, UpdateTodoRequest(isDone = !todo.isDone))
            } catch (_: Exception) {
                // Revert
                val taskId = _state.value.selectedTask?.id ?: return@launch
                loadTaskDetail(taskId)
            }
        }
    }

    fun deleteTodo(todoId: Int) {
        val taskId = _state.value.selectedTask?.id ?: return
        viewModelScope.launch {
            try {
                api.deleteTodo(todoId)
                loadTaskDetail(taskId)
            } catch (_: Exception) {}
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
