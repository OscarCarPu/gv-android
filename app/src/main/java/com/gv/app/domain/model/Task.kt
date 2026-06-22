package com.gv.app.domain.model

data class TaskDepRef(
    val id: Int,
    val name: String,
    val due_at: String?,
)

data class TodoResponse(
    val id: Int,
    val task_id: Int,
    val name: String,
    val is_done: Boolean,
)

data class TaskFullResponse(
    val id: Int,
    val project_id: Int?,
    val name: String,
    val description: String?,
    val due_at: String?,
    val started_at: String?,
    val finished_at: String?,
    val task_type: String,
    val recurrence: Int?,
    val priority: Int,
    val time_spent: Long,
    val todos: List<TodoResponse>,
    val depends_on: List<TaskDepRef>,
    val blocks: List<TaskDepRef>,
    val blocked: Boolean,
)

data class TaskResponse(
    val id: Int,
    val project_id: Int?,
    val name: String,
    val description: String?,
    val due_at: String?,
    val started_at: String?,
    val finished_at: String?,
    val task_type: String,
    val recurrence: Int?,
    val priority: Int,
    val depends_on: List<TaskDepRef>,
    val blocks: List<TaskDepRef>,
    val blocked: Boolean,
)

data class TaskByDueDateResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val due_at: String?,
    val started_at: String?,
    val task_type: String,
    val recurrence: Int?,
    val priority: Int,
    val time_spent: Long,
    val project_id: Int?,
    val project_name: String?,
    val project_due_at: String?,
    val depends_on: List<TaskDepRef>,
    val blocks: List<TaskDepRef>,
    val blocked: Boolean,
)

data class ActiveTreeNode(
    val id: Int,
    val type: String,
    val name: String,
    val description: String?,
    val due_at: String?,
    val started_at: String?,
    val task_type: String?,
    val recurrence: Int?,
    val priority: Int?,
    val children: List<ActiveTreeNode>?,
    val depends_on: List<TaskDepRef>?,
    val blocks: List<TaskDepRef>?,
    val blocked: Boolean?,
)

data class CreateTaskRequest(
    val project_id: Int?,
    val name: String,
    val description: String?,
    val due_at: String?,
    val task_type: String?,
    val recurrence: Int?,
    val priority: Int?,
)

data class UpdateTaskRequest(
    val name: String?,
    val description: String?,
    val due_at: String?,
    val project_id: Int?,
    val started_at: String?,
    val finished_at: String?,
    val task_type: String?,
    val recurrence: Int?,
    val priority: Int?,
)

data class CreateTodoRequest(
    val task_id: Int,
    val name: String,
)

data class UpdateTodoRequest(
    val name: String?,
    val is_done: Boolean?,
)

data class TimeEntryResponse(
    val id: Int,
    val task_id: Int,
    val started_at: String,
    val finished_at: String?,
    val comment: String?,
)

data class ActiveTimeEntryResponse(
    val id: Int,
    val task_id: Int,
    val started_at: String,
    val finished_at: String?,
    val comment: String?,
    val task_name: String,
    val task_description: String?,
    val task_type: String,
    val recurrence: Int?,
    val priority: Int,
    val project_name: String?,
)

data class CreateTimeEntryRequest(
    val task_id: Int,
    val started_at: String,
    val finished_at: String?,
    val comment: String?,
)

data class UpdateTimeEntryRequest(
    val task_id: Int?,
    val started_at: String?,
    val finished_at: String?,
    val comment: String?,
)

data class PaceBreakdown(
    val uniform_per_day_seconds: Long,
    val uniform_today_share_seconds: Long,
    val weighted_weekday_seconds: Long,
    val weighted_weekend_seconds: Long,
    val weighted_today_share_seconds: Long,
    val remaining_full_days: Int,
    val goal_reached: Boolean,
)

data class TimeEntrySummaryResponse(
    val today: Long,
    val week: Long,
    val daily_target_seconds: Long,
    val weekly_target_seconds: Long,
    val pace: PaceBreakdown,
)

data class PlanBlockResponse(
    val id: Int,
    val started_at: String,
    val ended_at: String,
    val task_id: Int?,
    val task_name: String?,
    val label: String,
    val note: String?,
    val task_type: String?,
    val task_recurrence: Int?,
    val task_started_at: String?,
    val task_finished_at: String?,
)

/** A time entry joined with its task — the agenda/day-list row shape. */
data class TimeEntryWithTaskResponse(
    val id: Int,
    val task_id: Int,
    val task_name: String,
    val task_type: String?,
    val recurrence: Int?,
    val priority: Int?,
    val project_id: Int?,
    val project_name: String?,
    val started_at: String,
    val finished_at: String?,
    val comment: String?,
    val task_finished_at: String?,
    val time_spent: Long,
)

/** Lightweight task option for the time-entry editor's task picker (built from cache). */
data class TaskOption(
    val id: Int,
    val name: String,
    val projectName: String?,
)

data class PlanTotals(
    val task_seconds: Long,
    val free_seconds: Long,
)

data class PlanTodayResponse(
    val date: String,
    val blocks: List<PlanBlockResponse>,
    val totals: PlanTotals,
    val budget: TimeEntrySummaryResponse,
)

data class ProjectListItem(
    val id: Int,
    val name: String,
)

/**
 * UI model for the running timer. Works whether or not the underlying time entry has reached
 * the server yet ([serverId] is null until synced); the screen ticks elapsed from [startedAt].
 */
data class ActiveTimer(
    val outboxId: String,
    val serverId: Int?,
    val taskId: Int,
    val taskName: String,
    val projectName: String?,
    val taskType: String?,
    val recurrence: Int?,
    val priority: Int?,
    val startedAt: String,
    val comment: String?,
)

// --- Project endpoints (added for parity with gv-api tasks/projects) ---

data class ProjectResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val due_at: String?,
    val parent_id: Int?,
    val started_at: String?,
    val finished_at: String?,
)

data class ProjectDetailResponse(
    val id: Int,
    val parent_id: Int?,
    val name: String,
    val description: String?,
    val due_at: String?,
    val started_at: String?,
    val finished_at: String?,
    val time_spent: Long,
)

/**
 * A child of a project, returned already sorted into status groups by the server.
 * Polymorphic on [type] ('project' | 'task'); project-only and task-only fields are nullable.
 */
data class ProjectChildNode(
    val id: Int,
    val type: String,
    val name: String,
    val description: String?,
    val due_at: String?,
    val started_at: String?,
    val finished_at: String?,
    val time_spent: Long,
    val parent_id: Int?,
    val project_id: Int?,
    val task_type: String?,
    val recurrence: Int?,
    val priority: Int?,
    val depends_on: List<TaskDepRef>?,
    val blocks: List<TaskDepRef>?,
    val blocked: Boolean?,
    val todos: List<TodoResponse>?,
)

data class ProjectChildrenResponse(
    val project: ProjectDetailResponse,
    val children: List<ProjectChildNode>,
)

/** Lightweight task row for pickers (reassign timer / time entry). */
data class TaskFastResponse(
    val id: Int,
    val name: String,
    val project_id: Int?,
    val project_name: String?,
    val task_type: String,
    val recurrence: Int?,
    val priority: Int,
)
