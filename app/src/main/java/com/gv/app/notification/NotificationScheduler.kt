package com.gv.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules and cancels the daily 11 AM [AlarmManager] alarm.
 *
 * After the alarm fires, [NotificationReceiver] calls [scheduleDailyAlarm] again to
 * arm the next day — a self-perpetuating daily chain.
 *
 * @param context Application context.
 * @param alarmManager Injected so unit tests can pass a mock.
 */
class NotificationScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)
) {

    companion object {
        const val PREFS_NAME = "notification_prefs"
        const val KEY_SCHEDULED = "alarm_scheduled"
        const val ACTION_DAILY_ALARM = "com.gv.app.ACTION_DAILY_ALARM"
    }

    /**
     * Arms the alarm only on first call. Safe to call on every app launch.
     * Tracks state in SharedPreferences so it's a no-op on subsequent launches.
     */
    fun scheduleIfNotAlreadyScheduled() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SCHEDULED, false)) return

        scheduleDailyAlarm()
        prefs.edit().putBoolean(KEY_SCHEDULED, true).apply()
    }

    /**
     * Arms (or re-arms) the exact 11 AM alarm for the next eligible time.
     * If 11 AM has already passed today, schedules for tomorrow.
     *
     * Uses exact alarm if SCHEDULE_EXACT_ALARM permission is granted, otherwise
     * falls back to inexact (up to ~1 hour flex).
     */
    fun scheduleDailyAlarm() {
        val triggerAt = nextElevenAm()
        val pi = buildPendingIntent()

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** Cancels the pending alarm. */
    fun cancelDailyAlarm() {
        alarmManager.cancel(buildPendingIntent())
    }

    /**
     * Returns the epoch-millisecond timestamp of the next 11:00:00 AM.
     * If 11 AM has already passed today, returns tomorrow's 11 AM.
     * Marked `internal` so tests can call it directly.
     */
    internal fun nextElevenAm(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_ALARM
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
