package com.gv.app.domain.model

import com.google.gson.annotations.SerializedName

// ── Habits ──────────────────────────────────────────────

data class Habit(
    val id: Int,
    val name: String,
    val description: String?,
    val frequency: String?,
    @SerializedName("target_min") val targetMin: Double?,
    @SerializedName("target_max") val targetMax: Double?,
    @SerializedName("recording_required") val recordingRequired: Boolean?,
    @SerializedName("log_value") val logValue: Double?,
    @SerializedName("period_value") val periodValue: Double?,
    @SerializedName("current_streak") val currentStreak: Int?,
    @SerializedName("longest_streak") val longestStreak: Int?
)

data class LogRequest(
    @SerializedName("habit_id") val habitId: Int,
    val date: String,
    val value: Double
)

data class CreateHabitRequest(
    val name: String,
    val description: String? = null,
    val frequency: String? = null,
    @SerializedName("target_min") val targetMin: Double? = null,
    @SerializedName("target_max") val targetMax: Double? = null,
    @SerializedName("recording_required") val recordingRequired: Boolean? = null
)

// ── Auth ────────────────────────────────────────────────

data class LoginRequest(val password: String)

data class TwoFactorRequest(val token: String, val code: String)

data class TokenResponse(val token: String)

data class ErrorResponse(val error: String)

// ── Projects ────────────────────────────────────────────

data class Project(
    val id: Int,
    @SerializedName("parent_id") val parentId: Int?,
    val name: String,
    val description: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("finished_at") val finishedAt: String?
)

data class ProjectDetail(
    val id: Int,
    @SerializedName("parent_id") val parentId: Int?,
    val name: String,
    val description: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("finished_at") val finishedAt: String?,
    @SerializedName("time_spent") val timeSpent: Long
)

data class ProjectFast(
    val id: Int,
    val name: String
)

data class CreateProjectRequest(
    val name: String,
    val description: String? = null,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("parent_id") val parentId: Int? = null
)

data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("finished_at") val finishedAt: String? = null
)

// ── Tasks ───────────────────────────────────────────────

data class Task(
    val id: Int,
    @SerializedName("project_id") val projectId: Int?,
    val name: String,
    val description: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("finished_at") val finishedAt: String?
)

data class TaskFull(
    val id: Int,
    @SerializedName("project_id") val projectId: Int?,
    val name: String,
    val description: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("finished_at") val finishedAt: String?,
    @SerializedName("time_spent") val timeSpent: Long,
    val todos: List<Todo>
)

data class TaskByDueDate(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("time_spent") val timeSpent: Long,
    @SerializedName("project_id") val projectId: Int?,
    @SerializedName("project_name") val projectName: String?,
    @SerializedName("project_due_at") val projectDueAt: String?
)

data class CreateTaskRequest(
    @SerializedName("project_id") val projectId: Int? = null,
    val name: String,
    val description: String? = null,
    @SerializedName("due_at") val dueAt: String? = null
)

data class UpdateTaskRequest(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("finished_at") val finishedAt: String? = null
)

// ── Todos ───────────────────────────────────────────────

data class Todo(
    val id: Int,
    @SerializedName("task_id") val taskId: Int,
    val name: String,
    @SerializedName("is_done") val isDone: Boolean
)

data class CreateTodoRequest(
    @SerializedName("task_id") val taskId: Int,
    val name: String
)

data class UpdateTodoRequest(
    val name: String? = null,
    @SerializedName("is_done") val isDone: Boolean? = null
)

// ── Time Entries ────────────────────────────────────────

data class TimeEntry(
    val id: Int,
    @SerializedName("task_id") val taskId: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("finished_at") val finishedAt: String?,
    val comment: String?
)

data class TimeEntrySummary(
    val today: Long,
    val week: Long
)

data class CreateTimeEntryRequest(
    @SerializedName("task_id") val taskId: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("finished_at") val finishedAt: String? = null,
    val comment: String? = null
)

data class UpdateTimeEntryRequest(
    @SerializedName("task_id") val taskId: Int? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("finished_at") val finishedAt: String? = null,
    val comment: String? = null
)

// ── Tree ────────────────────────────────────────────────

data class ActiveTreeNode(
    val id: Int,
    val type: String,
    val name: String,
    val description: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    val children: List<ActiveTreeNode>?
)
