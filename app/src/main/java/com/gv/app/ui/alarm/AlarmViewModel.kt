package com.gv.app.ui.alarm

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.alarm.AlarmConfig
import com.gv.app.alarm.AlarmPreferences
import com.gv.app.alarm.AlarmScheduler
import com.gv.app.spotify.Spotify
import com.gv.app.spotify.SpotifyAlarm
import com.gv.app.spotify.SpotifyAuthState
import com.gv.app.spotify.SpotifyPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PlaylistPickerState {
    object Disconnected : PlaylistPickerState()
    object Loading : PlaylistPickerState()
    data class Loaded(val playlists: List<SpotifyPlaylist>) : PlaylistPickerState()
    data class Error(val message: String) : PlaylistPickerState()
}

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AlarmPreferences(app)
    private val scheduler = AlarmScheduler(app)

    val config: StateFlow<AlarmConfig> = prefs.config
    val authState: StateFlow<SpotifyAuthState> = Spotify.state

    private val _picker = MutableStateFlow<PlaylistPickerState>(PlaylistPickerState.Disconnected)
    val picker: StateFlow<PlaylistPickerState> = _picker

    fun onTimeChanged(hour: Int, minute: Int) {
        prefs.setTime(hour, minute)
        if (prefs.config.value.enabled) {
            scheduler.scheduleDaily(hour, minute)
        }
    }

    fun onEnabledToggled(enabled: Boolean) {
        prefs.setEnabled(enabled)
        val c = prefs.config.value
        if (enabled) scheduler.scheduleDaily(c.hour, c.minute)
        else scheduler.cancel()
    }

    fun onPlaylistSelected(p: SpotifyPlaylist) {
        prefs.setPlaylist(p.uri, p.name, p.imageUrl)
    }

    fun onTestAlarm() {
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, Intent(ctx, SpotifyAlarm::class.java))
    }

    fun loadPlaylists() {
        if (authState.value != SpotifyAuthState.Connected) {
            _picker.value = PlaylistPickerState.Disconnected
            return
        }
        _picker.value = PlaylistPickerState.Loading
        viewModelScope.launch {
            Spotify.listMyPlaylists()
                .onSuccess { _picker.value = PlaylistPickerState.Loaded(it) }
                .onFailure { _picker.value = PlaylistPickerState.Error(it.message ?: "Unknown error") }
        }
    }

    fun resetPicker() {
        _picker.value = if (authState.value == SpotifyAuthState.Connected) {
            PlaylistPickerState.Loading
        } else {
            PlaylistPickerState.Disconnected
        }
    }
}
