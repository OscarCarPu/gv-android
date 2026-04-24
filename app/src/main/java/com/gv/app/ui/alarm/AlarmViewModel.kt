package com.gv.app.ui.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.gv.app.alarm.AlarmConfig
import com.gv.app.alarm.AlarmPreferences
import com.gv.app.alarm.AlarmScheduler
import kotlinx.coroutines.flow.StateFlow

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AlarmPreferences(app)
    private val scheduler = AlarmScheduler(app)

    val config: StateFlow<AlarmConfig> = prefs.config

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
}
