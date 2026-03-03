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

data class LoginRequest(val password: String)

data class TwoFactorRequest(val token: String, val code: String)

data class TokenResponse(val token: String)

data class ErrorResponse(val error: String)
