package com.gv.app.data.api

import com.gv.app.domain.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ────────────────────────────────────────────
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("login/2fa")
    suspend fun login2fa(@Body request: TwoFactorRequest): Response<TokenResponse>

    // ── Habits ──────────────────────────────────────────
    @GET("habits")
    suspend fun getHabits(@Query("date") date: String): List<Habit>

    @POST("habits")
    suspend fun createHabit(@Body request: CreateHabitRequest): Habit

    @POST("habits/log")
    suspend fun logHabit(@Body request: LogRequest)

    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: Int)

    // ── Projects ────────────────────────────────────────
    @GET("tasks/tree")
    suspend fun getActiveTree(): List<ActiveTreeNode>

    @GET("tasks/projects")
    suspend fun getProjects(): List<Project>

    @GET("tasks/projects/list-fast")
    suspend fun getProjectsFast(): List<ProjectFast>

    @GET("tasks/projects/{id}")
    suspend fun getProject(@Path("id") id: Int): ProjectDetail

    @GET("tasks/projects/{id}/children")
    suspend fun getProjectChildren(@Path("id") id: Int): List<ActiveTreeNode>

    @POST("tasks/projects")
    suspend fun createProject(@Body request: CreateProjectRequest): Project

    @PATCH("tasks/projects/{id}")
    suspend fun updateProject(@Path("id") id: Int, @Body request: UpdateProjectRequest): Project

    @DELETE("tasks/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Int)

    // ── Tasks ───────────────────────────────────────────
    @GET("tasks/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): TaskFull

    @GET("tasks/tasks/by-due-date")
    suspend fun getTasksByDueDate(): List<TaskByDueDate>

    @POST("tasks/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Task

    @PATCH("tasks/tasks/{id}")
    suspend fun updateTask(@Path("id") id: Int, @Body request: UpdateTaskRequest): Task

    @DELETE("tasks/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int)

    @GET("tasks/tasks/{id}/time-entries")
    suspend fun getTaskTimeEntries(@Path("id") id: Int): List<TimeEntry>

    // ── Todos ───────────────────────────────────────────
    @POST("tasks/todos")
    suspend fun createTodo(@Body request: CreateTodoRequest): Todo

    @PATCH("tasks/todos/{id}")
    suspend fun updateTodo(@Path("id") id: Int, @Body request: UpdateTodoRequest): Todo

    @DELETE("tasks/todos/{id}")
    suspend fun deleteTodo(@Path("id") id: Int)

    // ── Time Entries ────────────────────────────────────
    @GET("tasks/time-entries/active")
    suspend fun getActiveTimeEntry(): TimeEntry?

    @GET("tasks/time-entries/summary")
    suspend fun getTimeEntrySummary(): TimeEntrySummary

    @POST("tasks/time-entries")
    suspend fun createTimeEntry(@Body request: CreateTimeEntryRequest): TimeEntry

    @PATCH("tasks/time-entries/{id}")
    suspend fun updateTimeEntry(@Path("id") id: Int, @Body request: UpdateTimeEntryRequest): TimeEntry

    @DELETE("tasks/time-entries/{id}")
    suspend fun deleteTimeEntry(@Path("id") id: Int)
}
