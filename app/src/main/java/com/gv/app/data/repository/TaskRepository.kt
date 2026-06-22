package com.gv.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.PatchBody
import com.gv.app.data.local.db.ActiveTimerEntity
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_CREATE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_DELETE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_UPDATE
import com.gv.app.data.local.db.OutboxMutation.Companion.TMP_PREFIX
import com.gv.app.data.local.db.TaskDao
import com.gv.app.data.local.db.TasksSnapshotEntity
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.data.sync.Outbox
import com.gv.app.data.sync.OutboxHandler
import com.gv.app.data.sync.SyncOutcome
import com.gv.app.data.sync.SyncScheduler
import com.gv.app.data.sync.toSyncOutcome
import com.gv.app.domain.model.ActiveTimeEntryResponse
import com.gv.app.domain.model.ActiveTimer
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.CreateTimeEntryRequest
import com.gv.app.domain.model.PlanTodayResponse
import com.gv.app.domain.model.ProjectListItem
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.domain.model.TaskFullResponse
import com.gv.app.domain.model.TaskOption
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.domain.model.TimeEntryWithTaskResponse
import com.gv.app.domain.model.TodoResponse
import com.gv.app.domain.model.UpdateTodoRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Read model for the Tasks screen, reconstructed from the cached snapshot. */
data class TasksData(
    val byDueDate: List<TaskByDueDateResponse>,
    val tree: List<ActiveTreeNode>,
    val summary: TimeEntrySummaryResponse?,
    val plan: PlanTodayResponse?,
    val projects: List<ProjectListItem>,
)

/**
 * Offline-first tasks store.
 *
 * Reads: the screen collects [tasksData] from a cached JSON snapshot (instant, works offline)
 * while [refresh] reconciles in the background. The running timer is a structured cached row so
 * it can be started/stopped fully offline — the create is queued with a `tmp_` id and the real
 * server id is remapped onto the queue + cache once it syncs (handling create non-idempotency).
 * Task field/CRUD mutations are queued through the outbox and reconcile the snapshot on sync;
 * the task-detail drill-in and todos are direct calls (network-dependent by design).
 */
class TaskRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: TaskDao,
    private val outbox: Outbox,
    private val sync: SyncScheduler,
    private val gson: Gson = Gson(),
) : OutboxHandler, CacheRefresher {

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

            val dueList = due.await()
            val treeList = tree.await()
            val summaryRes = summary.await()
            val planRes = plan.await()
            val projectsRes = projects.await()

            // If the core lists couldn't load at all, treat as a failed refresh (keep cache).
            if (dueList == null && treeList == null && summaryRes == null) {
                return@coroutineScope ApiResult.Failure("Couldn't reach the server")
            }
            val current = dao.snapshotOnce()
            dao.upsertSnapshot(
                TasksSnapshotEntity(
                    byDueJson = dueList?.let { gson.toJson(it) } ?: current?.byDueJson ?: "[]",
                    treeJson = treeList?.let { gson.toJson(it) } ?: current?.treeJson ?: "[]",
                    summaryJson = summaryRes?.let { gson.toJson(it) } ?: current?.summaryJson,
                    planJson = planRes?.let { gson.toJson(it) } ?: current?.planJson,
                    projectsJson = projectsRes?.let { gson.toJson(it) } ?: current?.projectsJson ?: "[]",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            refreshTimer()
            ApiResult.Success(Unit)
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    private suspend fun refreshTimer() {
        // Don't clobber a locally-started timer whose create hasn't synced yet, nor one with any
        // queued mutation (e.g. an in-flight comment/assign/stop) — that would resurrect stale state.
        val local = dao.activeTimerOnce()
        if (local != null && (local.serverId == null || outbox.hasPending(ENTITY_TIME_ENTRY, local.outboxId))) return
        try {
            val resp = api.getActiveTimeEntry()
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) dao.upsertTimer(body.toEntity()) else dao.clearTimer()
            } else if (resp.code() == 404) {
                dao.clearTimer()
            }
        } catch (_: Exception) {
            // Network blip: keep the cached timer.
        }
    }

    // ----- Timer (fully offline-capable) -----

    suspend fun startOrAssignTimer(
        taskId: Int,
        taskName: String,
        projectName: String?,
        taskType: String?,
        recurrence: Int?,
        priority: Int?,
    ) {
        val existing = dao.activeTimerOnce()
        val now = nowIsoUtc()
        db.withTransaction {
            if (existing == null) {
                val tmp = TMP_PREFIX + UUID.randomUUID()
                dao.upsertTimer(
                    ActiveTimerEntity(
                        outboxId = tmp, serverId = null, taskId = taskId, taskName = taskName,
                        projectName = projectName, taskType = taskType, recurrence = recurrence,
                        priority = priority, startedAt = now, comment = null,
                    ),
                )
                outbox.enqueueCreate(
                    ENTITY_TIME_ENTRY, tmp,
                    gson.toJson(CreateTimeEntryRequest(task_id = taskId, started_at = now, finished_at = null, comment = null)),
                )
            } else {
                dao.upsertTimer(
                    existing.copy(
                        taskId = taskId, taskName = taskName, projectName = projectName,
                        taskType = taskType, recurrence = recurrence, priority = priority,
                    ),
                )
                outbox.enqueueUpdate(
                    ENTITY_TIME_ENTRY, existing.outboxId,
                    PatchBody.create().put("task_id", taskId).toJsonString(),
                )
            }
        }
        sync.requestFlush()
    }

    suspend fun stopTimer(comment: String?) {
        val active = dao.activeTimerOnce() ?: return
        val patch = PatchBody.create().put("finished_at", nowIsoUtc())
        if (!comment.isNullOrBlank()) patch.put("comment", comment)
        db.withTransaction {
            dao.clearTimer()
            outbox.enqueueUpdate(ENTITY_TIME_ENTRY, active.outboxId, patch.toJsonString())
        }
        sync.requestFlush()
        // NB: no immediate reconcile() — the stop is only queued, so re-fetching the active
        // entry now would resurrect the just-stopped timer. The handler reconciles after sync.
    }

    suspend fun cancelTimer() {
        val active = dao.activeTimerOnce() ?: return
        db.withTransaction {
            dao.clearTimer()
            outbox.enqueueDelete(ENTITY_TIME_ENTRY, active.outboxId)
        }
        sync.requestFlush()
    }

    suspend fun updateTimerComment(comment: String) {
        val active = dao.activeTimerOnce() ?: return
        db.withTransaction {
            dao.upsertTimer(active.copy(comment = comment.ifBlank { null }))
            outbox.enqueueUpdate(
                ENTITY_TIME_ENTRY, active.outboxId,
                PatchBody.create().putOrNull("comment", comment.ifBlank { null }).toJsonString(),
            )
        }
        sync.requestFlush()
    }

    // ----- Task mutations (queued) -----

    suspend fun startTask(taskId: Int) =
        enqueueTaskUpdate(taskId, PatchBody.create().put("started_at", nowIsoUtc()))

    suspend fun finishOrRenew(taskId: Int, taskType: String?, recurrence: Int?) {
        val patch = if (taskType == "recurring" && recurrence != null) {
            PatchBody.create().put("due_at", buildRecurringDueAt(recurrence))
        } else {
            PatchBody.create().put("finished_at", nowIsoUtc())
        }
        enqueueTaskUpdate(taskId, patch)
    }

    suspend fun updateTaskDetail(
        id: Int,
        name: String,
        description: String?,
        dueAt: String?,
        taskType: String,
        recurrence: Int?,
        priority: Int,
    ) {
        val patch = PatchBody.create()
            .put("name", name)
            .putOrNull("description", description)
            .putOrNull("due_at", dueAt)
            .put("task_type", taskType)
            .put("priority", priority)
        if (taskType == "recurring" && recurrence != null) patch.put("recurrence", recurrence) else patch.putNull("recurrence")
        enqueueTaskUpdate(id, patch)
    }

    private suspend fun enqueueTaskUpdate(id: Int, patch: PatchBody) {
        outbox.enqueueUpdate(ENTITY_TASK, id.toString(), patch.toJsonString())
        sync.requestFlush()
    }

    suspend fun createTask(req: CreateTaskRequest, startNow: Boolean) {
        val tmp = TMP_PREFIX + UUID.randomUUID()
        db.withTransaction {
            outbox.enqueueCreate(ENTITY_TASK, tmp, gson.toJson(req))
            if (startNow) {
                outbox.enqueueUpdate(ENTITY_TASK, tmp, PatchBody.create().put("started_at", nowIsoUtc()).toJsonString())
            }
        }
        sync.requestFlush()
    }

    suspend fun deleteTask(id: Int) {
        outbox.enqueueDelete(ENTITY_TASK, id.toString())
        sync.requestFlush()
    }

    // ----- Time-entry editing / agenda -----

    /** Network load of one day's time entries (agenda list). */
    suspend fun loadDayEntries(date: LocalDate): ApiResult<List<TimeEntryWithTaskResponse>> =
        safeApiCall { api.listTimeEntries(date.toString(), date.toString()) }

    /** Change the running timer's start; updates the cache (elapsed recomputes) + queues sync. */
    suspend fun editActiveTimerStart(startedAtIso: String) {
        val active = dao.activeTimerOnce() ?: return
        db.withTransaction {
            dao.upsertTimer(active.copy(startedAt = startedAtIso))
            outbox.enqueueUpdate(
                ENTITY_TIME_ENTRY, active.outboxId,
                PatchBody.create().put("started_at", startedAtIso).toJsonString(),
            )
        }
        sync.requestFlush()
    }

    /** Log a past entry spanning [startedAtIso]..[finishedAtIso] (offline-capable). */
    suspend fun createPastEntry(taskId: Int, startedAtIso: String, finishedAtIso: String, comment: String?) {
        val tmp = TMP_PREFIX + UUID.randomUUID()
        outbox.enqueueCreate(
            ENTITY_TIME_ENTRY, tmp,
            gson.toJson(CreateTimeEntryRequest(taskId, startedAtIso, finishedAtIso, comment?.ifBlank { null })),
        )
        sync.requestFlush()
    }

    /** Edit an existing (real-id) time entry: reassign task and/or change start/end/comment. */
    suspend fun editEntry(id: Int, taskId: Int?, startedAtIso: String?, finishedAtIso: String?, comment: String?) {
        val patch = PatchBody.create()
        taskId?.let { patch.put("task_id", it) }
        startedAtIso?.let { patch.put("started_at", it) }
        finishedAtIso?.let { patch.put("finished_at", it) }
        comment?.let { patch.putOrNull("comment", it.ifBlank { null }) }
        if (patch.isEmpty()) return
        outbox.enqueueUpdate(ENTITY_TIME_ENTRY, id.toString(), patch.toJsonString())
        sync.requestFlush()
    }

    suspend fun deleteEntry(id: Int) {
        outbox.enqueueDelete(ENTITY_TIME_ENTRY, id.toString())
        sync.requestFlush()
    }

    /** Task picker options derived from the cached snapshot (works offline). */
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

    // ----- Task detail + todos (direct / network-dependent) -----

    suspend fun loadTaskDetail(id: Int): ApiResult<TaskFullResponse> = safeApiCall { api.getTask(id) }

    suspend fun addTodo(taskId: Int, name: String): ApiResult<TodoResponse> =
        safeApiCall { api.createTodo(com.gv.app.domain.model.CreateTodoRequest(taskId, name.trim())) }

    suspend fun toggleTodo(todoId: Int, isDone: Boolean): ApiResult<TodoResponse> =
        safeApiCall { api.updateTodo(todoId, UpdateTodoRequest(name = null, is_done = isDone)) }

    suspend fun deleteTodo(todoId: Int): ApiResult<Unit> = safeApiCallNoBody { api.deleteTodo(todoId) }

    // ----- OutboxHandler -----

    override val entityTypes: Set<String> = setOf(ENTITY_TASK, ENTITY_TIME_ENTRY)

    override suspend fun sync(api: ApiService, mutation: OutboxMutation): SyncOutcome =
        when (mutation.entityType) {
            ENTITY_TIME_ENTRY -> syncTimeEntry(api, mutation)
            ENTITY_TASK -> syncTask(api, mutation)
            else -> SyncOutcome.DeadLetter("Unhandled type ${mutation.entityType}")
        }

    private suspend fun syncTimeEntry(api: ApiService, m: OutboxMutation): SyncOutcome =
        when (m.operation) {
            OP_CREATE -> {
                val req = gson.fromJson(m.payloadJson, CreateTimeEntryRequest::class.java)
                // Idempotency guard: a prior attempt may have committed before its response was
                // lost (WorkManager is at-least-once). On a retry, adopt the existing open entry
                // for this task instead of POSTing a duplicate. Only for active-timer starts
                // (finished_at == null) — a past entry (start..end) must never adopt the running timer.
                val adoptId = if (m.retryCount > 0 && req.finished_at == null) openEntryIdForTask(req.task_id) else null
                if (adoptId != null) {
                    adoptTimeEntry(m.entityId, adoptId)
                    SyncOutcome.Synced
                } else when (val r = safeApiCall { api.createTimeEntry(req) }) {
                    is ApiResult.Success -> {
                        adoptTimeEntry(m.entityId, r.data.id)
                        SyncOutcome.Synced
                    }
                    is ApiResult.Failure -> r.toSyncOutcome()
                }
            }
            OP_UPDATE -> {
                val id = m.entityId.toIntOrNull()
                    ?: return SyncOutcome.DeadLetter("Time entry never created (${m.entityId})")
                when (val r = safeApiCall { api.updateTimeEntryBody(id, PatchBody.bodyFromJson(m.payloadJson)) }) {
                    is ApiResult.Success -> {
                        // Stop/assign/comment landed: reconcile summary + active-timer truth.
                        runCatching { reconcile() }
                        SyncOutcome.Synced
                    }
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            OP_DELETE -> {
                val id = m.entityId.toIntOrNull() ?: return SyncOutcome.Synced
                when (val r = safeApiCallNoBody { api.deleteTimeEntry(id) }) {
                    is ApiResult.Success -> {
                        runCatching { reconcile() }
                        SyncOutcome.Synced
                    }
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            else -> SyncOutcome.DeadLetter("Bad op ${m.operation}")
        }

    /** The server's currently-open time entry id for [taskId], if any (idempotency adoption). */
    private suspend fun openEntryIdForTask(taskId: Int): Int? {
        val resp = runCatching { api.getActiveTimeEntry() }.getOrNull() ?: return null
        if (!resp.isSuccessful) return null
        val body = resp.body() ?: return null
        return if (body.task_id == taskId && body.finished_at == null) body.id else null
    }

    /** Remap queued follow-ups (stop/assign) + the cached timer from the tmp id to the real id. */
    private suspend fun adoptTimeEntry(tmpEntityId: String, realId: Int) {
        outbox.remapCreatedId(ENTITY_TIME_ENTRY, tmpEntityId, realId.toString())
        dao.activeTimerOnce()?.let { local ->
            if (local.outboxId == tmpEntityId) {
                dao.upsertTimer(local.copy(outboxId = realId.toString(), serverId = realId))
            }
        }
    }

    private suspend fun syncTask(api: ApiService, m: OutboxMutation): SyncOutcome {
        val outcome = when (m.operation) {
            OP_CREATE -> {
                val req = gson.fromJson(m.payloadJson, CreateTaskRequest::class.java)
                when (val r = safeApiCall { api.createTask(req) }) {
                    is ApiResult.Success -> {
                        outbox.remapCreatedId(ENTITY_TASK, m.entityId, r.data.id.toString())
                        SyncOutcome.Synced
                    }
                    is ApiResult.Failure -> r.toSyncOutcome()
                }
            }
            OP_UPDATE -> {
                val id = m.entityId.toIntOrNull()
                    ?: return SyncOutcome.DeadLetter("Task never created (${m.entityId})")
                when (val r = safeApiCall { api.updateTaskBody(id, PatchBody.bodyFromJson(m.payloadJson)) }) {
                    is ApiResult.Success -> SyncOutcome.Synced
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            OP_DELETE -> {
                val id = m.entityId.toIntOrNull() ?: return SyncOutcome.Synced
                when (val r = safeApiCallNoBody { api.deleteTask(id) }) {
                    is ApiResult.Success -> SyncOutcome.Synced
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            else -> SyncOutcome.DeadLetter("Bad op ${m.operation}")
        }
        // Reconcile the list/tree/summary once the change lands server-side.
        if (outcome is SyncOutcome.Synced) runCatching { reconcile() }
        return outcome
    }

    // ----- CacheRefresher -----

    override suspend fun refresh() {
        runCatching { reconcile() }
    }

    // ----- mapping -----

    private fun TasksSnapshotEntity.toData(): TasksData = TasksData(
        byDueDate = gson.fromJson(byDueJson, dueListType) ?: emptyList(),
        tree = gson.fromJson(treeJson, treeListType) ?: emptyList(),
        summary = summaryJson?.let { gson.fromJson(it, TimeEntrySummaryResponse::class.java) },
        plan = planJson?.let { gson.fromJson(it, PlanTodayResponse::class.java) },
        projects = gson.fromJson(projectsJson, projectsListType) ?: emptyList(),
    )

    private fun ActiveTimerEntity.toDomain() = ActiveTimer(
        outboxId = outboxId, serverId = serverId, taskId = taskId, taskName = taskName,
        projectName = projectName, taskType = taskType, recurrence = recurrence,
        priority = priority, startedAt = startedAt, comment = comment,
    )

    private fun ActiveTimeEntryResponse.toEntity() = ActiveTimerEntity(
        outboxId = id.toString(), serverId = id, taskId = task_id, taskName = task_name,
        projectName = project_name, taskType = task_type, recurrence = recurrence,
        priority = priority, startedAt = started_at, comment = comment,
    )

    companion object {
        const val ENTITY_TASK = "task"
        const val ENTITY_TIME_ENTRY = "time_entry"

        private val dueListType = object : TypeToken<List<TaskByDueDateResponse>>() {}.type
        private val treeListType = object : TypeToken<List<ActiveTreeNode>>() {}.type
        private val projectsListType = object : TypeToken<List<ProjectListItem>>() {}.type

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
