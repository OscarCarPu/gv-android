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
