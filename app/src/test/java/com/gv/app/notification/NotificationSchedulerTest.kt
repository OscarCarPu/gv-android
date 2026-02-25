package com.gv.app.notification

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class NotificationSchedulerTest {

    private lateinit var alarmManager: AlarmManager
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var scheduler: NotificationScheduler

    @BeforeTest
    fun setUp() {
        alarmManager = mockk()
        prefs        = mockk()
        prefsEditor  = mockk()
        context      = mockk()

        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.gv.app"
        every { context.applicationContext } returns context
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putBoolean(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } justRuns
        every { alarmManager.canScheduleExactAlarms() } returns true

        scheduler = NotificationScheduler(context, alarmManager)
    }

    @Test
    fun `scheduleIfNotAlreadyScheduled skips when already scheduled`() {
        every { prefs.getBoolean(NotificationScheduler.KEY_SCHEDULED, false) } returns true

        scheduler.scheduleIfNotAlreadyScheduled()

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `scheduleIfNotAlreadyScheduled arms alarm on first call`() {
        every { prefs.getBoolean(NotificationScheduler.KEY_SCHEDULED, false) } returns false
        justRun { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }

        scheduler.scheduleIfNotAlreadyScheduled()

        verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify { prefsEditor.putBoolean(NotificationScheduler.KEY_SCHEDULED, true) }
    }

    @Test
    fun `nextElevenAm is always in the future`() {
        assertTrue(scheduler.nextElevenAm() > System.currentTimeMillis())
    }

    @Test
    fun `nextElevenAm is set to exactly 11 AM`() {
        val cal = java.util.Calendar.getInstance().also {
            it.timeInMillis = scheduler.nextElevenAm()
        }
        assertTrue(cal.get(java.util.Calendar.HOUR_OF_DAY) == 11)
        assertTrue(cal.get(java.util.Calendar.MINUTE) == 0)
        assertTrue(cal.get(java.util.Calendar.SECOND) == 0)
    }

    @Test
    fun `scheduleDailyAlarm uses RTC_WAKEUP alarm type`() {
        val typeSlot = slot<Int>()
        justRun { alarmManager.setExactAndAllowWhileIdle(capture(typeSlot), any(), any()) }

        scheduler.scheduleDailyAlarm()

        assertTrue(typeSlot.captured == AlarmManager.RTC_WAKEUP)
    }

    @Test
    fun `cancelDailyAlarm cancels the pending intent`() {
        justRun { alarmManager.cancel(any<android.app.PendingIntent>()) }

        scheduler.cancelDailyAlarm()

        verify { alarmManager.cancel(any<android.app.PendingIntent>()) }
    }
}
