package com.gv.app.data.auth

import android.util.Log
import com.gv.app.data.api.ApiService
import com.gv.app.data.local.TokenManager
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.TwoFactorRequest

/**
 * Signs in without the user typing anything, using credentials baked in at build time.
 *
 * The API's login is two-step — password, then a TOTP code — so this answers the second step
 * itself with [Totp] rather than prompting. Both `AUTH_PASSWORD` and `AUTH_TOTP_SECRET` come
 * from `.env` / `.env.prod` via `buildConfigField`, and this runs in every build type, not
 * just debug.
 *
 * That does mean both factors ship inside the APK, so 2FA stops being a second factor for
 * anyone holding the file. That is the accepted trade here: this is a household app installed
 * on known phones, and the alternative is typing a password and a rotating code on every
 * cold start.
 *
 * The `semiprivate` flavour instead signs in with the semiprivate password (`totpSecret = null`):
 * gv-api answers that one with a 30-day `semi` token straight away, no second step, and the
 * lights and rutas endpoints accept it. See [com.gv.app.di.AppContainer.autoLogin].
 *
 * Leaving a needed value empty disables the whole thing and the normal login screen takes over,
 * which is what happens automatically for anyone building without those keys in their `.env`.
 */
class AutoLogin(
    private val api: ApiService,
    private val tokenManager: TokenManager,
    private val password: String,
    /** Null for the semiprivate tier, whose password alone is the session. */
    private val totpSecret: String?,
) {

    val isConfigured: Boolean
        get() = password.isNotBlank() && (totpSecret == null || totpSecret.isNotBlank())

    /**
     * Attempts a full sign-in. Returns true when a token was stored.
     *
     * Never throws: a failure here must fall back to the manual login screen, not crash the
     * app on launch.
     */
    suspend fun attempt(): Boolean {
        if (!isConfigured) return false
        if (tokenManager.tokenFlow.value != null) return true

        return try {
            val first = api.login(LoginRequest(password))
            val firstToken = first.body()?.token
            if (!first.isSuccessful || firstToken.isNullOrBlank()) {
                Log.w(TAG, "auto-login: password step failed (${first.code()})")
                return false
            }

            // A token of the wrong tier means the wrong password was baked into this build:
            // a semi token in the full app would be refused by every private endpoint.
            val semi = first.body()?.isSemiprivate == true
            if (semi != (totpSecret == null)) {
                Log.w(TAG, "auto-login: password is for the wrong tier (${first.body()?.kind})")
                return false
            }
            if (totpSecret == null) {
                tokenManager.saveToken(firstToken)
                return true
            }

            // The API answers the password step with a short-lived token that only the 2FA
            // endpoint accepts, so a successful first step is not yet a session.
            val code = Totp.generate(totpSecret)
            if (code == null) {
                Log.w(TAG, "auto-login: AUTH_TOTP_SECRET is not valid base32")
                return false
            }

            val second = api.login2fa(TwoFactorRequest(token = firstToken, code = code))
            val sessionToken = second.body()?.token
            if (!second.isSuccessful || sessionToken.isNullOrBlank()) {
                Log.w(TAG, "auto-login: 2FA step failed (${second.code()})")
                return false
            }

            tokenManager.saveToken(sessionToken)
            true
        } catch (e: Exception) {
            Log.w(TAG, "auto-login: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "AutoLogin"
    }
}
