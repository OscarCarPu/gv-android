package com.gv.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A durable, write-behind record of a local mutation that still needs to reach the server.
 *
 * Rows are flushed FIFO by [createdAt] by the OutboxFlushWorker. [payloadJson] is the exact
 * request body to replay (a typed-DTO JSON for CREATE, or a PatchBody JSON for UPDATE). For a
 * CREATE that hasn't reached the server yet, [entityId] is a client-generated `tmp_…` id; once
 * synced, the real server id is written back into the cache and any later queued rows are
 * remapped onto it.
 */
@Entity(tableName = "outbox")
data class OutboxMutation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val operation: String,
    val entityId: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val status: String = STATUS_PENDING,
    val errorBody: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_FAILED = "FAILED"

        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
        const val OP_UPSERT = "UPSERT"

        const val TMP_PREFIX = "tmp_"
    }
}

/** True when this row refers to an entity the server has never seen (a local-only CREATE). */
val OutboxMutation.isLocalOnly: Boolean
    get() = entityId.startsWith(OutboxMutation.TMP_PREFIX)
