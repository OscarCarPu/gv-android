package com.gv.app.domain.model

data class LoginRequest(val password: String)

data class TwoFactorRequest(val token: String, val code: String)

/**
 * `kind` is only set by `POST /login`: `"tmp"` means continue with `/login/2fa`, `"semi"` means
 * the token is already a (semiprivate, 30-day) session. `/login/2fa` leaves it null.
 */
data class TokenResponse(val token: String, val kind: String? = null) {
    val isSemiprivate: Boolean get() = kind == KIND_SEMI

    companion object {
        const val KIND_SEMI = "semi"
    }
}

data class ErrorResponse(val error: String)
