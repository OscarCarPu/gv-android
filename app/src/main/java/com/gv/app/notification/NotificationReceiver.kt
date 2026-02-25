package com.gv.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the daily 11 AM [AlarmManager] broadcast.
 *
 * 1. Posts the habit-reminder notification.
 * 2. Re-arms tomorrow's alarm (AlarmManager doesn't auto-repeat for exact alarms).
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_DAILY_ALARM) return

        NotificationHelper.showDailyNotification(context)
        NotificationScheduler(context.applicationContext).scheduleDailyAlarm()
    }
}
