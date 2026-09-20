package com.gv.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.PatchBody
import com.gv.app.data.local.db.ActiveTimerEntity
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.TaskDao
import com.gv.app.data.local.db.TasksSnapshotEntity
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.domain.model.ActiveTimeEntryResponse
import com.gv.app.domain.model.ActiveTimer
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.CreateTimeEntryRequest
import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.domain.model.PlanTodayResponse
import com.gv.app.domain.model.ProjectListItem
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.domain.model.TaskFastResponse
import com.gv.app.domain.model.TaskFullResponse
import com.gv.app.domain.model.TaskOption
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.domain.model.TimeEntryWithTaskResponse
import com.gv.app.domain.model.TodoResponse
import com.gv.app.domain.model.UpdateTodoRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read model for the Tasks screen, reconstructed from the cached snapshot. */
data class TasksData(
    val byDueDate: List<TaskByDueDateResponse>,
    val tree: List<ActiveTreeNode>,
    val summary: TimeEntrySummaryResponse?,
    val plan: PlanTodayResponse?,
    val projects: List<ProjectListItem>,
    val todayEntries: List<TimeEntryWithTaskResponse> = emptyList(),
    val freeBusy: List<DayFreeBusy> = emptyList(),
)

/**
 * Tasks store. Online-first, offline read-only.
 *
 * Reads render from a cached JSON snapshot so the screen paints instantly and still shows
 * something with no connection. Every write goes straight to the server and then re-reads
 * the affected state — the client never guesses what the server did with it.
 *
 * The running timer is the part this changed most. It used to be startable offline with a
 * `tmp_` id that got remapped once the create synced; now a timer only exists once the server
 * has issued its id, so [ActiveTimerEntity.serverId] is always real and stop/assign can simply
 * target it. That removes the whole class of bugs where a timer existed locally under an id
 * nothing else agreed with.
 */
class TaskRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: TaskDao,
    private val gate: OnlineGate,
    private val gson: Gson = Gson(),
) : CacheRefresher {

    fun tasksData(): Flow<TasksData?> = dao.snapshot().map { it?.toData() }

    fun activeTimer(): Flow<ActiveTimer?> = dao.activeTimer().map { it?.toDomain() }

    // ----- Refresh (cache reconciliation) -----

    /** Re-fetch all lists and the active timer; preserves the cached value for any that fail. */
    suspend fun reconcile(): ApiResult<Unit> = try {
        coroutineScope {
            val due = async { runCatching { api.getTasksByDueDate().bodyOrNull() }.getOrNull() }
            val tree = async { runCatching { api.getActiveTree().bodyOrNull() }.getOrNull() }
            val summary = async { runCatching { api.getTimeEntrySummary().bodyOrNull() }.getOrNull() }
            val plan = async { runCatching { api.getPlanToday().bodyOrNull() }.getOrNull() }
            val projects = async { runCatching { api.listProjectsFast().bodyOrNull() }.getOrNull() }
            val today = LocalDate.now()
            val entries = async { runCatching { api.listTimeEntries(today.toString(), today.toString()).bodyOrNull() }.getOrNull() }
            val freeBusy = async { runCatching { api.getFreeBusy(today.toString(), today.plusDays(7).toString()).bodyOrNull() }.getOrNull() }

            val dueList = due.await()
            val treeList = tree.await()
            val summaryRes = summary.await()
            val planRes = plan.await()
            val projectsRes = projects.await()
            val entriesRes = entries.await()
            val freeBusyRes = freeBusy.await()

            // If the core lists couldn't load at all, treat as a failed refresh (keep cache).
            if (dueList == null && treeList == null && summaryRes == null) {
                return@coroutineScope ApiResult.Failure("Couldn't reach the server")
            }
            // Read-modify-write, so it shares a transaction with refreshToday(): two writers
            // keeping the halves they each fetched would otherwise overwrite one another.
            db.withTransaction {
                val current = dao.snapshotOnce()
                dao.upsertSnapshot(
                    TasksSnapshotEntity(
                        byDueJson = dueList?.let { gson.toJson(it) } ?: current?.byDueJson ?: "[]",
                        treeJson = treeList?.let { gson.toJson(it) } ?: current?.treeJson ?: "[]",
                        summaryJson = summaryRes?.let { gson.toJson(it) } ?: current?.summaryJson,
                        planJson = planRes?.let { gson.toJson(it) } ?: current?.planJson,
                        projectsJson = projectsRes?.let { gson.toJson(it) } ?: current?.projectsJson ?: "[]",
                        entriesJson = entriesRes?.let { gson.toJson(it) } ?: current?.entriesJson,
                        freeBusyJson = freeBusyRes?.days?.let { gson.toJson(it) } ?: current?.freeBusyJson,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            refreshTimer()
            ApiResult.Success(Unit)
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    /** Pull the authoritative running timer. The server is the only source now. */
    private suspend fun refreshTimer() {
        try {
            val resp = api.getActiveTimeEntry()
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) dao.upsertTimer(body.toEntity()) else dao.clearTimer()
            } else if (resp.code() == 404) {
                dao.clearTimer()
            }
        } catch (_: Exception) {
            // Network blip: keep the cached timer rather than blanking a running one.
        }
    }

    /**
     * Re-read only what a timer change moves: today's entries (the plan's past half) and the
     * day/week summary. Far cheaper than a full [reconcile] and keeps the plan honest the
     * moment a timer starts or stops.
     */
    private suspend fun refreshToday() {
        val today = LocalDate.now().toString()
        val entries = runCatching { api.listTimeEntries(today, today).bodyOrNull() }.getOrNull() ?: return
        val summary = runCatching { api.getTimeEntrySummary().bodyOrNull() }.getOrNull()
        db.withTransaction {
            val current = dao.snapshotOnce() ?: return@withTransaction
            dao.upsertSnapshot(
                current.copy(
                    entriesJson = gson.toJson(entries),
                    summaryJson = summary?.let { gson.toJson(it) } ?: current.summaryJson,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // ----- Timer -----

    /** Start a timer on [taskId], or re-point the running one at it. */
    suspend fun startOrAssignTimer(taskId: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val existing = dao.activeTimerOnce()
        val result = if (existing == null) {
            safeApiCall {
                api.createTimeEntry(
                    CreateTimeEntryRequest(
                        task_id = taskId,
                        started_at = nowIsoUtc(),
                        finished_at = null,
                        comment = null,
                    ),
                )
            }.map { }
        } else {
            safeApiCall {
                api.updateTimeEntryBody(
                    existing.serverId,
                    PatchBody.create().put("task_id", taskId).toRequestBody(),
                )
            }.map { }
        }
        return result.thenRefreshTimer()
    }

    /** Finish the running timer at now and begin a fresh one on [taskId] (the row's "Stop Start"). */
    suspend fun stopAndStartTimer(taskId: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val stopped = stopTimer(comment = null)
        if (stopped is ApiResult.Failure) return stopped
        return startOrAssignTimer(taskId)
    }

    suspend fun stopTimer(comment: String?): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val active = dao.activeTimerOnce() ?: return ApiResult.Success(Unit)
        val patch = PatchBody.create().put("finished_at", nowIsoUtc())
        if (!comment.isNullOrBlank()) patch.put("comment", comment)
        return safeApiCall { api.updateTimeEntryBody(active.serverId, patch.toRequestBody()) }
            .map { }
            .thenRefreshTimer()
    }

    suspend fun cancelTimer(): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val active = dao.activeTimerOnce() ?: return ApiResult.Success(Unit)
        return safeApiCallNoBody { api.deleteTimeEntry(active.serverId) }.thenRefreshTimer()
    }

    suspend fun updateTimerComment(comment: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val active = dao.activeTimerOnce() ?: return ApiResult.Success(Unit)
        val patch = PatchBody.create().putOrNull("comment", comment.ifBlank { null })
        return safeApiCall { api.updateTimeEntryBody(active.serverId, patch.toRequestBody()) }
            .map { }
            .thenRefreshTimer()
    }

    /** Change the running timer's start time (elapsed recomputes from the server's value). */
    suspend fun editActiveTimerStart(startedAtIso: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val active = dao.activeTimerOnce() ?: return ApiResult.Success(Unit)
        val patch = PatchBody.create().put("started_at", startedAtIso)
        return safeApiCall { api.updateTimeEntryBody(active.serverId, patch.toRequestBody()) }
            .map { }
            .thenRefreshTimer()
    }

    // ----- Task mutations -----

    suspend fun startTask(taskId: Int): ApiResult<Unit> =
        patchTask(taskId, PatchBody.create().put("started_at", nowIsoUtc()))

    suspend fun finishOrRenew(taskId: Int, taskType: String?, recurrence: Int?): ApiResult<Unit> {
        val patch = if (taskType == "recurring" && recurrence != null) {
            PatchBody.create().put("due_at", buildRecurringDueAt(recurrence))
        } else {
            PatchBody.create().put("finished_at", nowIsoUtc())
        }
        return patchTask(taskId, patch)
    }

    suspend fun startProject(id: Int): ApiResult<Unit> =
        patchProject(id, PatchBody.create().put("started_at", nowIsoUtc()))

    suspend fun finishProject(id: Int): ApiResult<Unit> =
        patchProject(id, PatchBody.create().put("finished_at", nowIsoUtc()))

    private suspend fun patchProject(id: Int, patch: PatchBody): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateProject(id, patch.toRequestBody()) }.map { }.thenReconcile()
    }

    suspend fun updateTaskDetail(
        id: Int,
        name: String,
        description: String?,
        dueAt: String?,
        taskType: String,
        recurrence: Int?,
        priority: Int,
    ): ApiResult<Unit> {
        val patch = PatchBody.create()
            .put("name", name)
            .putOrNull("description", description)
            .putOrNull("due_at", dueAt)
            .put("task_type", taskType)
            .put("priority", priority)
        if (taskType == "recurring" && recurrence != null) {
            patch.put("recurrence", recurrence)
        } else {
            patch.putNull("recurrence")
        }
        return patchTask(id, patch)
    }

    private suspend fun patchTask(id: Int, patch: PatchBody): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateTaskBody(id, patch.toRequestBody()) }.map { }.thenReconcile()
    }

    suspend fun createTask(req: CreateTaskRequest, startNow: Boolean): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val created = safeApiCall { api.createTask(req) }
        if (created is ApiResult.Failure) return created
        val id = (created as ApiResult.Success).data.id
        if (startNow) {
            // Best-effort: the task exists either way, so a failure here must not read as
            // "create failed" — reconcile will show it unstarted.
            runCatching { api.updateTaskBody(id, PatchBody.create().put("started_at", nowIsoUtc()).toRequestBody()) }
        }
        return reconcile()
    }

    suspend fun deleteTask(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCallNoBody { api.deleteTask(id) }) {
            is ApiResult.Success -> reconcile()
            // Already gone is the outcome the caller wanted.
            is ApiResult.Failure -> if (r.code == 404) reconcile() else r
        }
    }

    // ----- Time-entry editing / agenda -----

    /** Network load of one day's time entries (agenda list). */
    suspend fun loadDayEntries(date: LocalDate): ApiResult<List<TimeEntryWithTaskResponse>> =
        safeApiCall { api.listTimeEntries(date.toString(), date.toString()) }

    /** Log a past entry spanning [startedAtIso]..[finishedAtIso]. */
    suspend fun createPastEntry(
        taskId: Int,
        startedAtIso: String,
        finishedAtIso: String,
        comment: String?,
    ): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall {
            api.createTimeEntry(
                CreateTimeEntryRequest(taskId, startedAtIso, finishedAtIso, comment?.ifBlank { null }),
            )
        }.map { }.thenRefreshTimer()
    }

    /** Edit an existing time entry: reassign task and/or change start/end/comment. */
    suspend fun editEntry(
        id: Int,
        taskId: Int?,
        startedAtIso: String?,
        finishedAtIso: String?,
        comment: String?,
    ): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val patch = PatchBody.create()
        taskId?.let { patch.put("task_id", it) }
        startedAtIso?.let { patch.put("started_at", it) }
        finishedAtIso?.let { patch.put("finished_at", it) }
        comment?.let { patch.putOrNull("comment", it.ifBlank { null }) }
        if (patch.isEmpty()) return ApiResult.Success(Unit)
        return safeApiCall { api.updateTimeEntryBody(id, patch.toRequestBody()) }.map { }.thenRefreshTimer()
    }

    suspend fun deleteEntry(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteTimeEntry(id) }.thenRefreshTimer()
    }

    /**
     * Every unfinished task in an active project, for the timer picker and the plan editor.
     * Read live (a stale list would offer tasks that are gone); the caller shows a failure as
     * "could not load" rather than as an empty account.
     */
    suspend fun pickerTasks(): ApiResult<List<TaskFastResponse>> = safeApiCall { api.listTasksFast() }

    /** Task picker options derived from the cached snapshot (so the picker works offline). */
    suspend fun taskOptions(): List<TaskOption> {
        val data = dao.snapshotOnce()?.toData() ?: return emptyList()
        val opts = LinkedHashMap<Int, TaskOption>()
        data.byDueDate.forEach { opts[it.id] = TaskOption(it.id, it.name, it.project_name) }
        fun walk(nodes: List<ActiveTreeNode>, projectName: String?) {
            for (n in nodes) {
                if (n.type == "task") {
                    if (!opts.containsKey(n.id)) opts[n.id] = TaskOption(n.id, n.name, projectName)
                } else {
                    walk(n.children ?: emptyList(), n.name)
                }
            }
        }
        walk(data.tree, null)
        return opts.values.sortedBy { it.name.lowercase() }
    }

    // ----- Task detail + todos -----

    suspend fun loadTaskDetail(id: Int): ApiResult<TaskFullResponse> = safeApiCall { api.getTask(id) }

    suspend fun addTodo(taskId: Int, name: String): ApiResult<TodoResponse> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createTodo(com.gv.app.domain.model.CreateTodoRequest(taskId, name.trim())) }
    }

    suspend fun toggleTodo(todoId: Int, isDone: Boolean): ApiResult<TodoResponse> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateTodo(todoId, UpdateTodoRequest(name = null, is_done = isDone)) }
    }

    suspend fun deleteTodo(todoId: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteTodo(todoId) }
    }

    // ----- CacheRefresher -----

    override suspend fun refresh() {
        runCatching { reconcile() }
    }

    // ----- helpers -----

    /** On success, re-read the timer and today's entries so the cache reflects the server. */
    private suspend fun ApiResult<Unit>.thenRefreshTimer(): ApiResult<Unit> {
        if (this is ApiResult.Success) {
            refreshTimer()
            refreshToday()
        }
        return this
    }

    /** On success, re-read the whole snapshot so lists reflect the server. */
    private suspend fun ApiResult<Unit>.thenReconcile(): ApiResult<Unit> {
        if (this is ApiResult.Success) reconcile()
        return this
    }

    // ----- mapping -----

    private fun TasksSnapshotEntity.toData(): TasksData = TasksData(
        byDueDate = gson.fromJson(byDueJson, dueListType) ?: emptyList(),
        tree = gson.fromJson(treeJson, treeListType) ?: emptyList(),
        summary = summaryJson?.let { gson.fromJson(it, TimeEntrySummaryResponse::class.java) },
        plan = planJson?.let { gson.fromJson(it, PlanTodayResponse::class.java) },
        projects = gson.fromJson(projectsJson, projectsListType) ?: emptyList(),
        todayEntries = entriesJson?.let { gson.fromJson<List<TimeEntryWithTaskResponse>>(it, entriesListType) } ?: emptyList(),
        freeBusy = freeBusyJson?.let { gson.fromJson<List<DayFreeBusy>>(it, freeBusyListType) } ?: emptyList(),
    )

    private fun ActiveTimerEntity.toDomain() = ActiveTimer(
        serverId = serverId, taskId = taskId, taskName = taskName,
        projectName = projectName, taskType = taskType, recurrence = recurrence,
        priority = priority, startedAt = startedAt, comment = comment,
    )

    private fun ActiveTimeEntryResponse.toEntity() = ActiveTimerEntity(
        serverId = id, taskId = task_id, taskName = task_name,
        projectName = project_name, taskType = task_type, recurrence = recurrence,
        priority = priority, startedAt = started_at, comment = comment,
    )

    companion object {
        private val dueListType = object : TypeToken<List<TaskByDueDateResponse>>() {}.type
        private val treeListType = object : TypeToken<List<ActiveTreeNode>>() {}.type
        private val projectsListType = object : TypeToken<List<ProjectListItem>>() {}.type
        private val entriesListType = object : TypeToken<List<TimeEntryWithTaskResponse>>() {}.type
        private val freeBusyListType = object : TypeToken<List<DayFreeBusy>>() {}.type

        private val isoUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

        private fun nowIsoUtc(): String =
            LocalDateTime.now().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().format(isoUtc)

        private fun buildRecurringDueAt(recurrence: Int): String =
            LocalDate.now().plusDays(recurrence.toLong()).atTime(12, 0)
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"))
                .toLocalDateTime().format(isoUtc)
    }
}

private fun <T> retrofit2.Response<T>.bodyOrNull(): T? = if (isSuccessful) body() else null
