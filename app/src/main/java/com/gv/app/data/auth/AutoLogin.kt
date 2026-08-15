package com.gv.app.data.auth

import android.util.Log
import com.gv.app.BuildConfig
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
 * Leaving either value empty disables the whole thing and the normal login screen takes over,
 * which is what happens automatically for anyone building without those keys in their `.env`.
 */
class AutoLogin(
    private val api: ApiService,
    private val tokenManager: TokenManager,
    private val password: String = BuildConfig.AUTH_PASSWORD,
    private val totpSecret: String = BuildConfig.AUTH_TOTP_SECRET,
) {

    val isConfigured: Boolean
        get() = password.isNotBlank() && totpSecret.isNotBlank()

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
