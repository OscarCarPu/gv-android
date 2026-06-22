package com.gv.app.data.api

import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateAccountRequest
import com.gv.app.domain.model.CreateCategoryRequest
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import com.gv.app.domain.model.LogHabitResponse
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.TokenResponse
import com.gv.app.domain.model.Transaction
import com.gv.app.domain.model.TwoFactorRequest
import com.gv.app.domain.model.ActiveTimeEntryResponse
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.CreateTimeEntryRequest
import com.gv.app.domain.model.CreateTodoRequest
import com.gv.app.domain.model.PlanTodayResponse
import com.gv.app.domain.model.ProjectListItem
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.domain.model.TaskFullResponse
import com.gv.app.domain.model.TaskResponse
import com.gv.app.domain.model.TimeEntryResponse
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.domain.model.TodoResponse
import com.gv.app.domain.model.UpdateAccountRequest
import com.gv.app.domain.model.UpdateCategoryRequest
import com.gv.app.domain.model.UpdateTaskRequest
import com.gv.app.domain.model.UpdateTimeEntryRequest
import com.gv.app.domain.model.UpdateTodoRequest
import com.gv.app.domain.model.UpdateTransactionRequest
import com.gv.app.domain.model.ConcelloMark
import com.gv.app.domain.model.CreateMarkRequest
import com.gv.app.domain.model.ProjectChildrenResponse
import com.gv.app.domain.model.ProjectDetailResponse
import com.gv.app.domain.model.ProjectResponse
import com.gv.app.domain.model.TaskFastResponse
import com.gv.app.domain.model.UpdateMarkRequest
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("health")
    suspend fun health(): Response<Unit>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("login/2fa")
    suspend fun login2fa(@Body request: TwoFactorRequest): Response<TokenResponse>

    @GET("habits")
    suspend fun getHabits(@Query("date") date: String): Response<List<HabitWithLog>>

    @POST("habits/log")
    suspend fun logHabit(@Body request: LogHabitRequest): Response<LogHabitResponse>

    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: Int): Response<Unit>

    @GET("finance/overview")
    suspend fun getFinanceOverview(): Response<Overview>

    @GET("finance/accounts")
    suspend fun listAccounts(): Response<List<Account>>

    @POST("finance/accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): Response<Account>

    @PUT("finance/accounts/{id}")
    suspend fun updateAccount(@Path("id") id: Int, @Body request: UpdateAccountRequest): Response<Account>

    @DELETE("finance/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Int): Response<Unit>

    @GET("finance/categories")
    suspend fun listCategories(): Response<List<Category>>

    @POST("finance/categories")
    suspend fun createCategory(@Body request: CreateCategoryRequest): Response<Category>

    @PUT("finance/categories/{id}")
    suspend fun updateCategory(@Path("id") id: Int, @Body request: UpdateCategoryRequest): Response<Category>

    @DELETE("finance/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Int): Response<Unit>

    @GET("finance/transactions")
    suspend fun listTransactions(@Query("account_id") accountId: Int? = null): Response<List<Transaction>>

    @GET("finance/transactions/{id}")
    suspend fun getTransaction(@Path("id") id: Int): Response<Transaction>

    @POST("finance/transactions")
    suspend fun createTransaction(@Body request: CreateTransactionRequest): Response<Transaction>

    @PUT("finance/transactions/{id}")
    suspend fun updateTransaction(@Path("id") id: Int, @Body request: UpdateTransactionRequest): Response<Transaction>

    @DELETE("finance/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: Int): Response<Unit>

    // --- Tasks ---

    @GET("tasks/tasks/by-due-date")
    suspend fun getTasksByDueDate(@Query("min_priority") minPriority: Int? = null): Response<List<TaskByDueDateResponse>>

    @GET("tasks/tree")
    suspend fun getActiveTree(@Query("min_priority") minPriority: Int? = null): Response<List<ActiveTreeNode>>

    @GET("tasks/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): Response<TaskFullResponse>

    @GET("tasks/tasks/list-fast")
    suspend fun listTasksFast(): Response<List<TaskFastResponse>>

    @POST("tasks/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Response<TaskResponse>

    @PATCH("tasks/tasks/{id}")
    suspend fun updateTask(@Path("id") id: Int, @Body request: UpdateTaskRequest): Response<TaskResponse>

    /**
     * Partial task update that can clear NullableTime fields and replace depends_on/blocks.
     * Body is built with [PatchBody] so explicit JSON nulls survive serialisation.
     */
    @PATCH("tasks/tasks/{id}")
    suspend fun updateTaskBody(@Path("id") id: Int, @Body body: RequestBody): Response<TaskResponse>

    @DELETE("tasks/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): Response<Unit>

    // --- Projects ---

    @GET("tasks/projects")
    suspend fun listProjects(): Response<List<ProjectResponse>>

    @GET("tasks/projects/{id}")
    suspend fun getProject(@Path("id") id: Int): Response<ProjectDetailResponse>

    @GET("tasks/projects/{id}/children")
    suspend fun getProjectChildren(@Path("id") id: Int): Response<ProjectChildrenResponse>

    /** Partial project update (clearable dates). Body built with [PatchBody]. */
    @PATCH("tasks/projects/{id}")
    suspend fun updateProject(@Path("id") id: Int, @Body body: RequestBody): Response<ProjectResponse>

    @DELETE("tasks/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Int): Response<Unit>

    @GET("tasks/projects/list-fast")
    suspend fun listProjectsFast(): Response<List<ProjectListItem>>

    // --- Todos ---

    @POST("tasks/todos")
    suspend fun createTodo(@Body request: CreateTodoRequest): Response<TodoResponse>

    @PATCH("tasks/todos/{id}")
    suspend fun updateTodo(@Path("id") id: Int, @Body request: UpdateTodoRequest): Response<TodoResponse>

    @DELETE("tasks/todos/{id}")
    suspend fun deleteTodo(@Path("id") id: Int): Response<Unit>

    // --- Time entries (timer) ---

    @POST("tasks/time-entries")
    suspend fun createTimeEntry(@Body request: CreateTimeEntryRequest): Response<TimeEntryResponse>

    @PATCH("tasks/time-entries/{id}")
    suspend fun updateTimeEntry(@Path("id") id: Int, @Body request: UpdateTimeEntryRequest): Response<TimeEntryResponse>

    /** Partial time-entry update that can clear finished_at (re-open). Body built with [PatchBody]. */
    @PATCH("tasks/time-entries/{id}")
    suspend fun updateTimeEntryBody(@Path("id") id: Int, @Body body: RequestBody): Response<TimeEntryResponse>

    @DELETE("tasks/time-entries/{id}")
    suspend fun deleteTimeEntry(@Path("id") id: Int): Response<Unit>

    @GET("tasks/time-entries")
    suspend fun listTimeEntries(
        @Query("start_time") startTime: String,
        @Query("end_time") endTime: String? = null,
    ): Response<List<com.gv.app.domain.model.TimeEntryWithTaskResponse>>

    @GET("tasks/time-entries/active")
    suspend fun getActiveTimeEntry(): Response<ActiveTimeEntryResponse>

    @GET("tasks/time-entries/summary")
    suspend fun getTimeEntrySummary(): Response<TimeEntrySummaryResponse>

    // --- Plan ---

    @GET("plan/today")
    suspend fun getPlanToday(): Response<PlanTodayResponse>

    // --- Rutas (Routes): Galicia municipality visit marks ---

    @GET("rutas/marks")
    suspend fun listMarks(): Response<List<ConcelloMark>>

    @GET("rutas/marks/{id}")
    suspend fun getMark(@Path("id") id: Int): Response<ConcelloMark>

    @POST("rutas/marks")
    suspend fun createMark(@Body request: CreateMarkRequest): Response<ConcelloMark>

    @PUT("rutas/marks/{id}")
    suspend fun updateMark(@Path("id") id: Int, @Body request: UpdateMarkRequest): Response<ConcelloMark>

    @DELETE("rutas/marks/{id}")
    suspend fun deleteMark(@Path("id") id: Int): Response<Unit>
}
