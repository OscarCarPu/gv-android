package com.gv.app.domain.model

data class HabitWithLog(
    val id: Int,
    val name: String,
    val description: String?,
    val frequency: String,
    val target_min: Double?,
    val target_max: Double?,
    val recording_required: Boolean,
    val log_value: Double?,
    val period_value: Double,
    val current_streak: Int,
    val longest_streak: Int,
)

data class LogHabitRequest(
    val habit_id: Int,
    val date: String,
    val value: Double,
)

data class LogHabitResponse(val status: String)
