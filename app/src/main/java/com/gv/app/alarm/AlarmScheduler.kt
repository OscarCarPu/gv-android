package com.gv.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

class AlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java),
) {

    fun scheduleDaily(hour: Int, minute: Int) {
        val triggerAt = nextOccurrence(hour, minute)
        val pi = buildPendingIntent()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
    }

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_ALARM_FIRED = "com.gv.app.ACTION_ALARM_FIRED"
        private const val REQUEST_CODE = 1001
    }
}
