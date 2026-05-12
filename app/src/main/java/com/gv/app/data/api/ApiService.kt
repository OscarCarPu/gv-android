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
    suspend fun getTasksByDueDate(): Response<List<TaskByDueDateResponse>>

    @GET("tasks/tree")
    suspend fun getActiveTree(): Response<List<ActiveTreeNode>>

    @GET("tasks/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): Response<TaskFullResponse>

    @POST("tasks/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Response<TaskResponse>

    @PATCH("tasks/tasks/{id}")
    suspend fun updateTask(@Path("id") id: Int, @Body request: UpdateTaskRequest): Response<TaskResponse>

    @DELETE("tasks/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): Response<Unit>

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

    @DELETE("tasks/time-entries/{id}")
    suspend fun deleteTimeEntry(@Path("id") id: Int): Response<Unit>

    @GET("tasks/time-entries/active")
    suspend fun getActiveTimeEntry(): Response<ActiveTimeEntryResponse>

    @GET("tasks/time-entries/summary")
    suspend fun getTimeEntrySummary(): Response<TimeEntrySummaryResponse>

    // --- Plan ---

    @GET("plan/today")
    suspend fun getPlanToday(): Response<PlanTodayResponse>
}
