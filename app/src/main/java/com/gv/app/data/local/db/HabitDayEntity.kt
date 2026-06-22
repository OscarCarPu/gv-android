package com.gv.app.data.local.db

import androidx.room.Entity

/**
 * Cached habit state for one (habit, day). The server computes log/period/streak values
 * relative to the date, so the cache is keyed by both id and date — letting the date-paged
 * UI render any day entirely from cache.
 */
@Entity(tableName = "habit_day", primaryKeys = ["id", "date"])
data class HabitDayEntity(
    val id: Int,
    val date: String,
    val name: String,
    val description: String?,
    val frequency: String,
    val targetMin: Double?,
    val targetMax: Double?,
    val recordingRequired: Boolean,
    val logValue: Double?,
    val periodValue: Double,
    val currentStreak: Int,
    val longestStreak: Int,
)
