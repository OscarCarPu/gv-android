package com.gv.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC, id ASC")
    suspend fun pending(): List<OutboxMutation>

    /** Fresh read of a single row — reflects any entityId remap / status change since pending(). */
    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun byId(id: Long): OutboxMutation?

    @Query("SELECT COUNT(*) FROM outbox WHERE entityType = :type AND entityId = :entityId AND status = 'PENDING'")
    suspend fun countPendingForEntity(type: String, entityId: String): Int

    @Query(
        "SELECT * FROM outbox WHERE status = 'PENDING' AND entityType = :type " +
            "AND entityId = :entityId AND operation = :op ORDER BY id DESC LIMIT 1",
    )
    suspend fun findPending(type: String, entityId: String, op: String): OutboxMutation?

    @Query(
        "SELECT * FROM outbox WHERE entityType = :type AND entityId = :entityId " +
            "AND status = 'PENDING' ORDER BY id ASC",
    )
    suspend fun pendingForEntity(type: String, entityId: String): List<OutboxMutation>

    @Insert
    suspend fun insert(mutation: OutboxMutation): Long

    @Update
    suspend fun update(mutation: OutboxMutation)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM outbox WHERE entityType = :type AND entityId = :entityId AND status = 'PENDING'")
    suspend fun deletePendingForEntity(type: String, entityId: String)

    /** Re-point queued rows from a temporary CREATE id to the server-assigned id. */
    @Query("UPDATE outbox SET entityId = :realId WHERE entityType = :type AND entityId = :tmpId")
    suspend fun remapEntityId(type: String, tmpId: String, realId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE status = 'FAILED' ORDER BY createdAt ASC")
    fun failedFlow(): Flow<List<OutboxMutation>>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM outbox WHERE status = 'FAILED'")
    suspend fun clearFailed()
}
