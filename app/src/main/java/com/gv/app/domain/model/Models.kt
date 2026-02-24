package com.gv.app.domain.model

import com.google.gson.annotations.SerializedName

data class Habit(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("log_value") val logValue: Double?
)

data class LogRequest(
    @SerializedName("habit_id") val habitId: Int,
    val date: String,
    val value: Double
)
