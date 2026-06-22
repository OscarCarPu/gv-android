package com.gv.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks_snapshot WHERE id = 0")
    fun snapshot(): Flow<TasksSnapshotEntity?>

    @Query("SELECT * FROM tasks_snapshot WHERE id = 0")
    suspend fun snapshotOnce(): TasksSnapshotEntity?

    @Upsert
    suspend fun upsertSnapshot(snapshot: TasksSnapshotEntity)

    @Query("SELECT * FROM active_timer WHERE id = 0")
    fun activeTimer(): Flow<ActiveTimerEntity?>

    @Query("SELECT * FROM active_timer WHERE id = 0")
    suspend fun activeTimerOnce(): ActiveTimerEntity?

    @Upsert
    suspend fun upsertTimer(timer: ActiveTimerEntity)

    @Query("DELETE FROM active_timer")
    suspend fun clearTimer()
}
