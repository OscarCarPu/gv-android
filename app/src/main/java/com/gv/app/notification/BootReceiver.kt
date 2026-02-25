package com.gv.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and re-arms the daily alarm.
 *
 * AlarmManager alarms are cleared on device reboot. Without this receiver,
 * notifications would stop firing until the user opens the app again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Bypass the "already scheduled" guard — alarm must always be restored after reboot
        NotificationScheduler(context.applicationContext).scheduleDailyAlarm()
    }
}
