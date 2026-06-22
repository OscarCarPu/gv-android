package com.gv.app.data.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gv.app.data.local.db.OutboxDao
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_CREATE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_DELETE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_UPDATE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_UPSERT
import com.gv.app.data.local.db.OutboxMutation.Companion.TMP_PREFIX

/**
 * Enqueue façade for the write-behind queue. Applies the collapse rules that keep the queue
 * small and correct when the user makes rapid or conflicting offline edits:
 *
 *  - **UPSERT / UPDATE** merge into an existing pending row for the same entity (newer field
 *    values win), so a flurry of taps becomes a single network call.
 *  - **DELETE** drops all pending rows for the entity first; if the entity is local-only
 *    (a CREATE that never reached the server) the delete is a net no-op and nothing is queued.
 *  - **CREATE** is never collapsed.
 *
 * JSON merge uses [JsonObject], whose `toString()` preserves explicit nulls — so a cleared
 * NullableTime field survives being merged with a later edit.
 */
class Outbox(
    private val dao: OutboxDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun enqueueCreate(entityType: String, tmpId: String, payloadJson: String): Long =
        dao.insert(row(entityType, OP_CREATE, tmpId, payloadJson))

    suspend fun enqueueUpdate(entityType: String, entityId: String, payloadJson: String) =
        mergeOrInsert(entityType, OP_UPDATE, entityId, payloadJson)

    suspend fun enqueueUpsert(entityType: String, entityId: String, payloadJson: String) =
        mergeOrInsert(entityType, OP_UPSERT, entityId, payloadJson)

    /** After a CREATE syncs, re-point queued follow-up rows from the tmp id to the server id. */
    suspend fun remapCreatedId(entityType: String, tmpId: String, realId: String) =
        dao.remapEntityId(entityType, tmpId, realId)

    /** True if there's an un-synced mutation for this entity (don't clobber it from the server). */
    suspend fun hasPending(entityType: String, entityId: String): Boolean =
        dao.countPendingForEntity(entityType, entityId) > 0

    suspend fun enqueueDelete(entityType: String, entityId: String) {
        val pending = dao.pendingForEntity(entityType, entityId)
        val localOnly = entityId.startsWith(TMP_PREFIX) ||
            pending.any { it.operation == OP_CREATE }
        dao.deletePendingForEntity(entityType, entityId)
        if (!localOnly) {
            dao.insert(row(entityType, OP_DELETE, entityId, "{}"))
        }
    }

    private suspend fun mergeOrInsert(entityType: String, op: String, entityId: String, payloadJson: String) {
        val existing = dao.findPending(entityType, entityId, op)
        if (existing != null) {
            dao.update(existing.copy(payloadJson = mergeJson(existing.payloadJson, payloadJson)))
        } else {
            dao.insert(row(entityType, op, entityId, payloadJson))
        }
    }

    private fun row(entityType: String, op: String, entityId: String, payloadJson: String) =
        OutboxMutation(
            entityType = entityType,
            operation = op,
            entityId = entityId,
            payloadJson = payloadJson,
            createdAt = now(),
        )

    companion object {
        /** Overlay [overlay]'s members onto [base]; preserves explicit nulls. */
        fun mergeJson(base: String, overlay: String): String {
            val b = runCatching { JsonParser.parseString(base).asJsonObject }.getOrNull() ?: JsonObject()
            val o = runCatching { JsonParser.parseString(overlay).asJsonObject }.getOrNull() ?: return overlay
            o.entrySet().forEach { (k, v) -> b.add(k, v) }
            return b.toString()
        }
    }
}
