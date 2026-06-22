package com.gv.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * A visited concello, cached for offline use. Keyed by concello [name] (matches the API + web).
 * [serverId] is null until a locally-created mark syncs; [outboxId] is the key used in the
 * outbox (a `tmp_…` id pre-sync, the server id afterwards).
 */
@Entity(tableName = "concello_mark")
data class ConcelloMarkEntity(
    @PrimaryKey val name: String,
    val serverId: Int?,
    val visitedOn: String,
    val description: String,
    val outboxId: String,
)

@Dao
interface RutasDao {

    @Query("SELECT * FROM concello_mark")
    fun marks(): Flow<List<ConcelloMarkEntity>>

    @Query("SELECT * FROM concello_mark WHERE name = :name")
    suspend fun find(name: String): ConcelloMarkEntity?

    @Upsert
    suspend fun upsert(mark: ConcelloMarkEntity)

    @Upsert
    suspend fun upsertAll(marks: List<ConcelloMarkEntity>)

    @Query("DELETE FROM concello_mark WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("SELECT * FROM concello_mark WHERE serverId IS NULL")
    suspend fun pendingCreates(): List<ConcelloMarkEntity>

    @Query("DELETE FROM concello_mark WHERE serverId IS NOT NULL")
    suspend fun deleteSynced()
}
