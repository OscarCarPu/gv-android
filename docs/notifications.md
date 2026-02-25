# Daily Habit Reminder Notification

Source: `app/src/main/java/com/gv/app/notification/`

Fires a daily notification at **11 AM**. Tapping it opens the app and launches the
Set All wizard automatically.

---

## Components

| File | Responsibility |
|------|---------------|
| `NotificationHelper` | Creates the channel; posts the notification |
| `NotificationScheduler` | Manages the `AlarmManager` alarm; guards against duplicates via SharedPreferences |
| `NotificationReceiver` | Receives the 11 AM broadcast; posts notification and re-arms tomorrow's alarm |
| `BootReceiver` | Restores the alarm after device reboot |

---

## Flow

```
AlarmManager fires → NotificationReceiver → shows notification + reschedules tomorrow
User taps notification → MainActivity reads EXTRA_OPEN_WIZARD → SetWizardDialog opens
Device reboots → BootReceiver → re-arms alarm
```

---

## Testing without waiting until 11 AM

```bash
# Trigger notification
adb shell am broadcast -a com.gv.app.ACTION_DAILY_ALARM \
  -n com.gv.app/.notification.NotificationReceiver

# Simulate reboot
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
  -n com.gv.app/.notification.BootReceiver

# Unit tests (no device needed)
./gradlew test
```
