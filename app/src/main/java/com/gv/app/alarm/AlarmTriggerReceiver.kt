package com.gv.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gv.app.spotify.SpotifyAlarm

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM_FIRED) return

        val app = context.applicationContext
        app.startForegroundService(Intent(app, SpotifyAlarm::class.java))

        val prefs = AlarmPreferences(app)
        val config = prefs.config.value
        if (config.enabled) {
            AlarmScheduler(app).scheduleDaily(config.hour, config.minute)
        }
    }
}
