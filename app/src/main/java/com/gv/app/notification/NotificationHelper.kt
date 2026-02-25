package com.gv.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gv.app.MainActivity

/**
 * Creates the notification channel and posts the daily habit-reminder notification.
 *
 * Call [createChannel] once at app startup (safe to call multiple times).
 * Call [showDailyNotification] from [NotificationReceiver] when the alarm fires.
 */
object NotificationHelper {

    const val CHANNEL_ID = "daily_habits_reminder"
    const val CHANNEL_NAME = "Daily Habits Reminder"
    const val NOTIFICATION_ID = 1001

    /** Intent extra key that tells MainActivity to open the wizard. */
    const val EXTRA_OPEN_WIZARD = "open_wizard"

    /**
     * Creates the notification channel.
     * Safe to call on every app launch — the system ignores duplicate registrations.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminds you to log your habits at 11 AM every day"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * Posts the daily habit-reminder notification.
     * Tapping it launches [MainActivity] with [EXTRA_OPEN_WIZARD] = true.
     */
    fun showDailyNotification(context: Context) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_WIZARD, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time to log your habits")
            .setContentText("Tap to open the daily wizard")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
