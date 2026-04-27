package com.gv.app.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AlarmConfig(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val playlistUri: String,
    val playlistName: String,
    val playlistImageUrl: String?,
)

class AlarmPreferences(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val _config = MutableStateFlow(read())
    val config: StateFlow<AlarmConfig> = _config

    fun setTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        _config.value = _config.value.copy(hour = hour, minute = minute)
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _config.value = _config.value.copy(enabled = enabled)
    }

    fun setPlaylist(uri: String, name: String, imageUrl: String?) {
        prefs.edit()
            .putString(KEY_PLAYLIST_URI, uri)
            .putString(KEY_PLAYLIST_NAME, name)
            .putString(KEY_PLAYLIST_IMAGE_URL, imageUrl)
            .apply()
        _config.value = _config.value.copy(
            playlistUri = uri,
            playlistName = name,
            playlistImageUrl = imageUrl,
        )
    }

    private fun read() = AlarmConfig(
        hour = prefs.getInt(KEY_HOUR, 7),
        minute = prefs.getInt(KEY_MINUTE, 0),
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        playlistUri = prefs.getString(KEY_PLAYLIST_URI, DEFAULT_PLAYLIST_URI)!!,
        playlistName = prefs.getString(KEY_PLAYLIST_NAME, DEFAULT_PLAYLIST_NAME)!!,
        playlistImageUrl = prefs.getString(KEY_PLAYLIST_IMAGE_URL, null),
    )

    companion object {
        private const val PREFS_NAME = "gv_alarm"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PLAYLIST_URI = "playlist_uri"
        private const val KEY_PLAYLIST_NAME = "playlist_name"
        private const val KEY_PLAYLIST_IMAGE_URL = "playlist_image_url"
        private const val DEFAULT_PLAYLIST_URI = "spotify:playlist:56ou9OedNCUjQmBGWhN73a"
        private const val DEFAULT_PLAYLIST_NAME = "Boom Boom"
    }
}
