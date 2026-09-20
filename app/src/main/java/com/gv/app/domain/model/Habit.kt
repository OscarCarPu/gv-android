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

/**
 * `POST /habits`. Only the name is required; the API defaults the rest (daily, recording
 * required). Nulls are omitted on the wire, which is what "not given" means here.
 */
data class CreateHabitRequest(
    val name: String,
    val description: String?,
    val frequency: String?,
    val target_min: Double?,
    val target_max: Double?,
    val recording_required: Boolean?,
)

/**
 * `PUT /habits/{id}` replaces the habit: a target or description that is left out is cleared,
 * which is exactly what an emptied form field should do, so omitted nulls are correct here.
 */
data class UpdateHabitRequest(
    val name: String,
    val description: String?,
    val frequency: String,
    val target_min: Double?,
    val target_max: Double?,
    val recording_required: Boolean,
)
