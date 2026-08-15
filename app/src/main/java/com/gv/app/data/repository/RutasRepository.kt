package com.gv.app.data.repository

import androidx.room.withTransaction
import com.gv.app.data.api.ApiService
import com.gv.app.data.local.db.ConcelloMarkEntity
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.RutasDao
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.domain.model.CreateMarkRequest
import com.gv.app.domain.model.UpdateMarkRequest
import kotlinx.coroutines.flow.Flow

/**
 * Store for Routes (Galicia concello visit marks). Online-first, offline read-only.
 *
 * The map geometry is bundled in assets, so the map itself always renders; the cached marks
 * keep it meaningful with no connection. Saving or removing a mark requires a connection —
 * marks are keyed by concello name server-side, and the previous queued version had to invent
 * temporary ids and adopt-on-retry rules to avoid duplicating them. Writing straight through
 * makes all of that unnecessary.
 */
class RutasRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: RutasDao,
    private val gate: OnlineGate,
) : CacheRefresher {

    fun marks(): Flow<List<ConcelloMarkEntity>> = dao.marks()

    suspend fun reconcile(): ApiResult<Unit> = when (val r = safeApiCall { api.listMarks() }) {
        is ApiResult.Success -> {
            val entities = r.data.map {
                ConcelloMarkEntity(
                    name = it.name,
                    serverId = it.id,
                    visitedOn = it.visited_on.take(10),
                    description = it.description,
                )
            }
            // Swap the whole set atomically — the server list is the truth.
            db.withTransaction {
                dao.deleteAllMarks()
                dao.upsertAll(entities)
            }
            ApiResult.Success(Unit)
        }

        is ApiResult.Failure -> r
    }

    suspend fun saveMark(name: String, visitedOn: String, description: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val existing = dao.find(name)
        val result = if (existing?.serverId == null) {
            safeApiCall { api.createMark(CreateMarkRequest(name, visitedOn, description)) }
        } else {
            safeApiCall { api.updateMark(existing.serverId, UpdateMarkRequest(visitedOn, description)) }
        }
        return when (result) {
            is ApiResult.Success -> reconcile()
            is ApiResult.Failure -> result
        }
    }

    suspend fun removeMark(name: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val existing = dao.find(name) ?: return ApiResult.Success(Unit)
        val id = existing.serverId ?: return ApiResult.Success(Unit)
        return when (val r = safeApiCallNoBody { api.deleteMark(id) }) {
            is ApiResult.Success -> reconcile()
            is ApiResult.Failure -> if (r.code == 404) reconcile() else r
        }
    }

    override suspend fun refresh() {
        runCatching { reconcile() }
    }
}
