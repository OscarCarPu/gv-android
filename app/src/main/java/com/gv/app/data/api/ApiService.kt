package com.gv.app.data.api

import com.gv.app.domain.model.Account
import com.gv.app.domain.model.AuthUrlResponse
import com.gv.app.domain.model.CalendarAccount
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.GoogleCalendar
import com.gv.app.domain.model.MoveEventRequest
import com.gv.app.domain.model.MoveEventResult
import com.gv.app.domain.model.SyncResult
import com.gv.app.domain.model.UpdateCalendarAccountRequest
import com.gv.app.domain.model.UpdateCalendarRequest
import com.gv.app.domain.model.UpdateEventRequest
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateAccountRequest
import com.gv.app.domain.model.CreateCategoryRequest
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import com.gv.app.domain.model.LogHabitResponse
import com.gv.app.domain.model.LightCommandRequest
import com.gv.app.domain.model.LightState
import com.gv.app.domain.model.LightStatesResponse
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

    // --- Domotics lights ---
    // Served by gv-api like everything else; the BLE bridge sits behind it, not in front.

    /** Every configured bulb's state. [force] skips the API's short read cache. */
    @GET("domotics/lights/state")
    suspend fun getLightStates(@Query("force") force: Int? = null): Response<LightStatesResponse>

    @GET("domotics/lights/{id}")
    suspend fun getLightState(
        @Path("id") id: String,
        @Query("force") force: Int? = null,
    ): Response<LightState>

    /** Apply one command; the response is the bulb's resulting state. */
    @POST("domotics/lights/{id}")
    suspend fun sendLightCommand(
        @Path("id") id: String,
        @Body command: LightCommandRequest,
    ): Response<LightState>

    // --- Calendar: a local mirror of the user's Google calendars, editable from here ---
    //
    // Google stays the source of truth: reads are served from gv-api's mirror, writes go to
    // Google first and are stored only once it accepts them. Recurrence is expanded server-side,
    // so `getCalendarEvents` already returns occurrences.

    @GET("calendar/accounts")
    suspend fun listCalendarAccounts(): Response<List<CalendarAccount>>

    /** The Google consent URL for adding one account. Expires in minutes; 503 if unconfigured. */
    @POST("calendar/accounts/auth-url")
    suspend fun calendarAuthUrl(): Response<AuthUrlResponse>

    @PATCH("calendar/accounts/{id}")
    suspend fun updateCalendarAccount(
        @Path("id") id: Int,
        @Body request: UpdateCalendarAccountRequest,
    ): Response<CalendarAccount>

    /** Stops the push channels, revokes the grant at Google, drops the local copy. */
    @DELETE("calendar/accounts/{id}")
    suspend fun deleteCalendarAccount(@Path("id") id: Int): Response<Unit>

    /** Throws the local copy of one account away and rebuilds it from scratch. */
    @POST("calendar/accounts/{id}/resync")
    suspend fun resyncCalendarAccount(@Path("id") id: Int): Response<SyncResult>

    @GET("calendar/calendars")
    suspend fun listCalendars(): Response<List<GoogleCalendar>>

    /** Local preferences only (`sync_enabled`, `visible`, `color_override`). */
    @PATCH("calendar/calendars/{id}")
    suspend fun updateCalendar(
        @Path("id") id: Int,
        @Body request: UpdateCalendarRequest,
    ): Response<GoogleCalendar>

    /**
     * Everything happening in `[from, to)`, recurring series already expanded. Both bounds are
     * required, `to` is exclusive, and a range longer than two years is refused.
     */
    @GET("calendar/events")
    suspend fun getCalendarEvents(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("visible_only") visibleOnly: Boolean? = null,
        @Query("calendar_ids") calendarIds: String? = null,
        @Query("account_ids") accountIds: String? = null,
    ): Response<List<CalendarEvent>>

    /*
     * `ref` is an event id ("12") or one occurrence ("12@2026-08-20T07:00:00Z"), taken verbatim
     * from `instance_id` and never assembled here.
     *
     * It is deliberately passed through Retrofit's normal (unencoded) @Path: OkHttp leaves `@`
     * and `:` alone in a path segment, which is what gv-api's parser expects. Percent-encoding
     * them would break it — chi routes on the *raw* path and hands the handler the encoded
     * segment, where `strings.Cut(ref, "@")` then finds no separator and the id fails to parse.
     */
    @GET("calendar/events/{ref}")
    suspend fun getCalendarEvent(@Path("ref") ref: String): Response<CalendarEvent>

    @POST("calendar/events")
    suspend fun createCalendarEvent(@Body request: CreateEventRequest): Response<CalendarEvent>

    @PATCH("calendar/events/{ref}")
    suspend fun updateCalendarEvent(
        @Path("ref") ref: String,
        @Body request: UpdateEventRequest,
    ): Response<CalendarEvent>

    @DELETE("calendar/events/{ref}")
    suspend fun deleteCalendarEvent(
        @Path("ref") ref: String,
        @Query("scope") scope: String? = null,
        @Query("send_updates") sendUpdates: String? = null,
    ): Response<Unit>

    /** Between calendars of the same account Google moves it; across accounts it is recreated. */
    @POST("calendar/events/{ref}/move")
    suspend fun moveCalendarEvent(
        @Path("ref") ref: String,
        @Body request: MoveEventRequest,
    ): Response<MoveEventResult>

    /** Sync now — everything, or one calendar. The poll and push notifications do this anyway. */
    @POST("calendar/sync")
    suspend fun syncCalendar(@Query("calendar_id") calendarId: Int? = null): Response<SyncResult>
}
