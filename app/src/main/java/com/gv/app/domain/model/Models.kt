package com.gv.app.domain.model

data class LoginRequest(val password: String)

data class TwoFactorRequest(val token: String, val code: String)

data class TokenResponse(val token: String)

data class ErrorResponse(val error: String)
