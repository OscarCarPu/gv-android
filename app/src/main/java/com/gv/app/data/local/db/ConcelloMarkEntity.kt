package com.gv.app.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A visited concello, cached so the map stays meaningful offline. Keyed by concello [name]
 * (matches the API + web). Every row is a copy of a server row, so [serverId] is always set
 * once it has been fetched.
 */
@Entity(tableName = "concello_mark")
data class ConcelloMarkEntity(
    @PrimaryKey val name: String,
    val serverId: Int?,
    val visitedOn: String,
    val description: String,
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

    @Query("DELETE FROM concello_mark")
    suspend fun deleteAllMarks()
}
