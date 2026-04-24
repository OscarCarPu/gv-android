package com.gv.app.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AlarmConfig(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
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

    private fun read() = AlarmConfig(
        hour = prefs.getInt(KEY_HOUR, 7),
        minute = prefs.getInt(KEY_MINUTE, 0),
        enabled = prefs.getBoolean(KEY_ENABLED, false),
    )

    companion object {
        private const val PREFS_NAME = "gv_alarm"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_ENABLED = "enabled"
    }
}
