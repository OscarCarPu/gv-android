# Notification Skeleton

Source: `app/src/main/java/com/gv/app/notification/`

No alarm or notification is active in the current build. The classes below are kept as scaffolding so a future feature can wire a scheduled notification without reintroducing the plumbing from scratch.

---

## What's in place

| File | Responsibility |
|------|---------------|
| `NotificationHelper` | Channel creation + `showDailyNotification` (posts to `MainActivity`). Not called at startup. |
| `NotificationScheduler` | Builds `AlarmManager` alarms via `scheduleDailyAlarm()` / `scheduleIfNotAlreadyScheduled()`. Not invoked anywhere at runtime. |
| `NotificationReceiver` | Explicit-component `BroadcastReceiver`. Wired in `AndroidManifest.xml` (`exported="false"`). Only fires if something schedules an alarm pointing at it. |

Permissions retained in `AndroidManifest.xml`: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`.

---

## Reactivating a scheduled notification

To re-enable a daily notification, add back to `MainActivity.onCreate` (in the right order):

```kotlin
NotificationHelper.createChannel(this)
NotificationScheduler(applicationContext).scheduleIfNotAlreadyScheduled()
// Also request POST_NOTIFICATIONS at runtime before posting.
```

Tune `NotificationScheduler.nextElevenAm()` if a different trigger time is needed, and update `NotificationHelper.showDailyNotification()` content + intent extras to fit the new feature.

For boot-survival, re-add a `BootReceiver` class + `<receiver>` manifest entry + the `RECEIVE_BOOT_COMPLETED` permission. The previous BootReceiver just called `scheduleDailyAlarm()` on `BOOT_COMPLETED`.
