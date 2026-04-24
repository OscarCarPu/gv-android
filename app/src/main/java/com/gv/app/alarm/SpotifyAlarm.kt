package com.gv.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gv.app.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Single file owning ALL Spotify + volume-ramp logic.
 * Callers only know: `startForegroundService(Intent(ctx, SpotifyAlarm::class.java))`.
 */
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
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        connectAndPlay()
        rampJob = scope.launch { rampVolume() }
        return START_NOT_STICKY
    }

    private fun setVolumePercent(pct: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((max * pct) / 100f).roundToInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun connectAndPlay() {
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote
                remote.playerApi.play(BOOM_BOOM_PLAYLIST_URI)
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
        stopEverything()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        rampJob?.cancel()
        rampJob = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText("Boom Boom is playing")
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
        private const val BOOM_BOOM_PLAYLIST_URI = "spotify:playlist:56ou9OedNCUjQmBGWhN73a"
        private const val CHANNEL_ID = "gv_alarm"
        private const val NOTIFICATION_ID = 2001
        private const val START_PERCENT = 5
        private const val END_PERCENT = 70
        private const val STEP_PERCENT = 5
        private const val STEP_INTERVAL_MS = 40_000L
    }
}
