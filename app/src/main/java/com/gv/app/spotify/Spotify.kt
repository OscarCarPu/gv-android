package com.gv.app.spotify

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.gv.app.BuildConfig
import com.gv.app.alarm.AlarmPreferences
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.roundToInt

// =====================================================================================
// Single-file Spotify integration:
//   - SpotifyAlarm   : foreground Service that plays the configured playlist (App Remote)
//   - SpotifyAuth    : OAuth PKCE flow (Custom Tabs + token storage + refresh)
//   - SpotifyAuthCallbackActivity : receives the com.gv.app://spotify-callback redirect
//   - Spotify        : public entry point used by the UI (state + listMyPlaylists + login)
// Callers outside this file must not import com.spotify.* or hit the Web API directly.
// =====================================================================================

// ----- Public types ------------------------------------------------------------------

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String?,
)

enum class SpotifyAuthState { Disconnected, Connected }

// ----- Public entry point ------------------------------------------------------------

object Spotify {
    val state: StateFlow<SpotifyAuthState> get() = SpotifyAuth.state

    fun startLogin(activity: Activity) = SpotifyAuth.startLogin(activity)

    fun logout() = SpotifyAuth.logout()

    suspend fun listMyPlaylists(): Result<List<SpotifyPlaylist>> = runCatching {
        val response = SpotifyHttp.api.getMyPlaylists()
        response.items.map { p ->
            SpotifyPlaylist(
                id = p.id,
                name = p.name,
                uri = p.uri,
                imageUrl = p.images?.firstOrNull()?.url,
            )
        }
    }
}

// ----- Foreground service: plays the configured playlist ----------------------------

class SpotifyAlarm : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var rampJob: Job? = null
    private var appRemote: SpotifyAppRemote? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cancelInFlight()
        val cfg = AlarmPreferences(this).config.value
        startForeground(
            NOTIFICATION_ID,
            buildNotification(cfg.playlistName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        connectAndPlay(cfg.playlistUri)
        rampJob = scope.launch { rampVolume() }
        return START_NOT_STICKY
    }

    private fun setVolumePercent(pct: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((max * pct) / 100f).roundToInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun connectAndPlay(playlistUri: String) {
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote
                remote.playerApi.play(playlistUri)
                    .setErrorCallback { Log.e(TAG, "play() error", it) }
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "Spotify connect failed", throwable)
            }
        })
    }

    private suspend fun rampVolume() {
        var pct = START_PERCENT
        while (pct <= END_PERCENT) {
            setVolumePercent(pct)
            if (pct >= END_PERCENT) break
            delay(STEP_INTERVAL_MS)
            pct += STEP_PERCENT
        }
        stopSelf()
    }

    override fun onDestroy() {
        cancelInFlight()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun cancelInFlight() {
        rampJob?.cancel()
        rampJob = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
    }

    private fun buildNotification(playlistName: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText("$playlistName is playing")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SpotifyAlarm"
        private const val CHANNEL_ID = "gv_alarm"
        private const val NOTIFICATION_ID = 2001
        private const val START_PERCENT = 1
        private const val END_PERCENT = 70
        private const val STEP_PERCENT = 1
        private const val STEP_INTERVAL_MS = 5_000L
    }
}

// ----- OAuth PKCE state machine ------------------------------------------------------

internal object SpotifyAuth {

    private const val PREFS = "gv_spotify"
    private const val KEY_VERIFIER = "code_verifier"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val SCOPES = "playlist-read-private playlist-read-collaborative"
    private const val AUTH_URL = "https://accounts.spotify.com/authorize"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val TAG = "SpotifyAuth"

    private lateinit var prefs: SharedPreferences
    private val tokenMutex = Mutex()
    private val _state = MutableStateFlow(SpotifyAuthState.Disconnected)
    val state: StateFlow<SpotifyAuthState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_REFRESH, null) != null) {
            _state.value = SpotifyAuthState.Connected
        }
    }

    fun startLogin(activity: Activity) {
        init(activity)
        val verifier = generateCodeVerifier()
        prefs.edit().putString(KEY_VERIFIER, verifier).apply()
        val challenge = sha256Base64Url(verifier)
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(activity, uri)
    }

    suspend fun exchangeCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = prefs.getString(KEY_VERIFIER, null)
                ?: error("Missing code verifier — login flow not initiated from this device")
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
                .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                .add("code_verifier", verifier)
                .build()
            val token = postForToken(body)
            persistToken(token)
            prefs.edit().remove(KEY_VERIFIER).apply()
            _state.value = SpotifyAuthState.Connected
        }
    }

    /** Returns a non-expired access token, refreshing if needed. Null if not connected. */
    suspend fun accessToken(): String? = tokenMutex.withLock {
        val access = prefs.getString(KEY_ACCESS, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        if (access != null && System.currentTimeMillis() < expiresAt - 60_000L) {
            return access
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    .build()
                val token = postForToken(body)
                persistToken(token, fallbackRefresh = refresh)
                token.access_token
            }.getOrElse {
                Log.e(TAG, "refresh failed", it)
                null
            }
        }
    }

    fun logout() {
        if (!::prefs.isInitialized) return
        prefs.edit().clear().apply()
        _state.value = SpotifyAuthState.Disconnected
    }

    private fun postForToken(body: FormBody): TokenResponse {
        val req = Request.Builder()
            .url(TOKEN_URL)
            .post(body)
            .build()
        SpotifyHttp.bareClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Token endpoint ${resp.code}: $raw")
            return SpotifyHttp.gson.fromJson(raw, TokenResponse::class.java)
        }
    }

    private fun persistToken(token: TokenResponse, fallbackRefresh: String? = null) {
        val expiresAt = System.currentTimeMillis() + token.expires_in * 1000L
        prefs.edit()
            .putString(KEY_ACCESS, token.access_token)
            .putString(KEY_REFRESH, token.refresh_token ?: fallbackRefresh)
            .putLong(KEY_EXPIRES, expiresAt)
            .apply()
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}

// ----- Activity that catches the redirect URI ---------------------------------------

class SpotifyAuthCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpotifyAuth.init(this)
        val data = intent?.data
        val code = data?.getQueryParameter("code")
        val error = data?.getQueryParameter("error")
        if (code != null) {
            lifecycleScope.launch {
                SpotifyAuth.exchangeCode(code)
                finish()
            }
        } else {
            Log.e("SpotifyAuthCallback", "No code in redirect (error=$error)")
            finish()
        }
    }
}

// ----- HTTP / Web API ---------------------------------------------------------------

private object SpotifyHttp {
    val gson = com.google.gson.Gson()

    val bareClient: OkHttpClient = OkHttpClient.Builder().build()

    private val authedClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = runBlocking { SpotifyAuth.accessToken() }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .build()

    val api: SpotifyWebApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/")
        .client(authedClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyWebApi::class.java)
}

private interface SpotifyWebApi {
    @GET("v1/me/playlists")
    suspend fun getMyPlaylists(@Query("limit") limit: Int = 50): PlaylistsResponse
}

private data class PlaylistsResponse(val items: List<PlaylistDto>)
private data class PlaylistDto(
    val id: String,
    val name: String,
    val uri: String,
    val images: List<ImageDto>?,
)
private data class ImageDto(val url: String)
private data class TokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Long,
    val token_type: String?,
    val scope: String?,
)
