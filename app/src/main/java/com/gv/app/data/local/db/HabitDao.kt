package com.gv.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habit_day WHERE date = :date ORDER BY id")
    fun habitsForDate(date: String): Flow<List<HabitDayEntity>>

    @Query("SELECT * FROM habit_day WHERE id = :habitId AND date = :date LIMIT 1")
    suspend fun find(habitId: Int, date: String): HabitDayEntity?

    @Upsert
    suspend fun upsert(row: HabitDayEntity)

    @Upsert
    suspend fun upsertAll(rows: List<HabitDayEntity>)

    /** Prune habits no longer returned for a date (e.g. deleted), keeping the fresh set. */
    @Query("DELETE FROM habit_day WHERE date = :date AND id NOT IN (:keepIds)")
    suspend fun deleteMissingForDate(date: String, keepIds: List<Int>)

    @Query("DELETE FROM habit_day WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Int)
}
