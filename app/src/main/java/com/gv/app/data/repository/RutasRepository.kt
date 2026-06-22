package com.gv.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.gv.app.data.api.ApiService
import com.gv.app.data.local.db.ConcelloMarkEntity
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_CREATE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_DELETE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_UPDATE
import com.gv.app.data.local.db.OutboxMutation.Companion.TMP_PREFIX
import com.gv.app.data.local.db.RutasDao
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.data.sync.Outbox
import com.gv.app.data.sync.OutboxHandler
import com.gv.app.data.sync.SyncOutcome
import com.gv.app.data.sync.SyncScheduler
import com.gv.app.data.sync.toSyncOutcome
import com.gv.app.domain.model.CreateMarkRequest
import com.gv.app.domain.model.UpdateMarkRequest
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Offline-first store for Routes (Galicia concello visit marks). Marks are cached in Room and
 * served instantly/offline; create/update/delete go through the outbox (keyed by concello name
 * locally, by the entity's [ConcelloMarkEntity.outboxId] in the queue). The local geometry is
 * bundled in assets, so the map renders fully offline; only the marks sync.
 */
class RutasRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: RutasDao,
    private val outbox: Outbox,
    private val sync: SyncScheduler,
    private val gson: Gson = Gson(),
) : OutboxHandler, CacheRefresher {

    fun marks(): Flow<List<ConcelloMarkEntity>> = dao.marks()

    suspend fun reconcile(): ApiResult<Unit> = when (val r = safeApiCall { api.listMarks() }) {
        is ApiResult.Success -> {
            // Replace synced rows with the server set; keep locally-pending creates.
            val serverEntities = r.data.map {
                ConcelloMarkEntity(
                    name = it.name, serverId = it.id, visitedOn = it.visited_on.take(10),
                    description = it.description, outboxId = it.id.toString(),
                )
            }
            val pendingNames = dao.pendingCreates().map { it.name }.toSet()
            db.withTransaction {
                dao.deleteSynced()
                dao.upsertAll(serverEntities.filter { it.name !in pendingNames })
            }
            ApiResult.Success(Unit)
        }
        is ApiResult.Failure -> r
    }

    suspend fun saveMark(name: String, visitedOn: String, description: String) {
        val existing = dao.find(name)
        db.withTransaction {
            if (existing == null) {
                val tmp = TMP_PREFIX + UUID.randomUUID()
                dao.upsert(ConcelloMarkEntity(name, serverId = null, visitedOn, description, outboxId = tmp))
                outbox.enqueueCreate(ENTITY_MARK, tmp, gson.toJson(CreateMarkRequest(name, visitedOn, description)))
            } else {
                dao.upsert(existing.copy(visitedOn = visitedOn, description = description))
                outbox.enqueueUpdate(ENTITY_MARK, existing.outboxId, gson.toJson(UpdateMarkRequest(visitedOn, description)))
            }
        }
        sync.requestFlush()
    }

    suspend fun removeMark(name: String) {
        val existing = dao.find(name) ?: return
        db.withTransaction {
            dao.deleteByName(name)
            outbox.enqueueDelete(ENTITY_MARK, existing.outboxId)
        }
        sync.requestFlush()
    }

    // ----- OutboxHandler -----

    override val entityTypes: Set<String> = setOf(ENTITY_MARK)

    override suspend fun sync(api: ApiService, mutation: OutboxMutation): SyncOutcome =
        when (mutation.operation) {
            OP_CREATE -> {
                val req = gson.fromJson(mutation.payloadJson, CreateMarkRequest::class.java)
                // Idempotency guard: on a retry, adopt an existing mark with this name (marks are
                // unique by concello) instead of POSTing a duplicate.
                val adoptId = if (mutation.retryCount > 0) existingMarkId(req.name) else null
                if (adoptId != null) {
                    adoptMark(req.name, mutation.entityId, adoptId)
                    SyncOutcome.Synced
                } else when (val r = safeApiCall { api.createMark(req) }) {
                    is ApiResult.Success -> {
                        adoptMark(req.name, mutation.entityId, r.data.id)
                        SyncOutcome.Synced
                    }
                    is ApiResult.Failure -> r.toSyncOutcome()
                }
            }
            OP_UPDATE -> {
                val id = mutation.entityId.toIntOrNull()
                    ?: return SyncOutcome.DeadLetter("Mark never created (${mutation.entityId})")
                val req = gson.fromJson(mutation.payloadJson, UpdateMarkRequest::class.java)
                when (val r = safeApiCall { api.updateMark(id, req) }) {
                    is ApiResult.Success -> SyncOutcome.Synced
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            OP_DELETE -> {
                val id = mutation.entityId.toIntOrNull() ?: return SyncOutcome.Synced
                when (val r = safeApiCallNoBody { api.deleteMark(id) }) {
                    is ApiResult.Success -> SyncOutcome.Synced
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            else -> SyncOutcome.DeadLetter("Bad op ${mutation.operation}")
        }

    private suspend fun existingMarkId(name: String): Int? {
        val resp = runCatching { api.listMarks() }.getOrNull() ?: return null
        if (!resp.isSuccessful) return null
        return resp.body()?.firstOrNull { it.name == name }?.id
    }

    private suspend fun adoptMark(name: String, tmpEntityId: String, realId: Int) {
        outbox.remapCreatedId(ENTITY_MARK, tmpEntityId, realId.toString())
        dao.find(name)?.let {
            if (it.outboxId == tmpEntityId) dao.upsert(it.copy(serverId = realId, outboxId = realId.toString()))
        }
    }

    override suspend fun refresh() {
        runCatching { reconcile() }
    }

    companion object {
        const val ENTITY_MARK = "mark"
    }
}
