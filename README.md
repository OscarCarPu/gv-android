# gv-android

Android interface for [gestor-vida](../gestor-vida) — a personal life management system for tracking habits, tasks, and finances.

## Stack

- **Kotlin** + **Jetpack Compose** — UI
- **Retrofit** — REST API communication with the gestor-vida backend
- **Room** — local persistence
- **Clean Architecture** — data / domain / ui layer separation

## Project structure

```
app/src/main/java/com/gv/app/
├── data/
│   ├── api/          # Retrofit service interfaces
│   └── local/        # Room database, DAOs, entities
├── domain/
│   ├── model/        # Shared models: Habit, Task, Transaction
│   └── usecase/      # Business logic, one class per use case
├── notification/     # Daily 11 AM reminder — AlarmManager, receivers, helper
├── ui/
│   ├── components/   # Reusable Compose widgets
│   ├── habits/       # Habit tracking screens
│   ├── tasks/        # Task management screens
│   └── finance/      # Finance screens (API-backed)
└── MainActivity.kt   # App entry point
```

## Requirements

- Android SDK (API 35), build-tools 36.1.0
- `ANDROID_HOME` pointing to the SDK root
- ADB for device deployment

## Release setup (one-time)

Generate a keystore and create `keystore.properties` in the project root (both are gitignored):

```bash
keytool -genkeypair -v \
  -keystore gv.jks \
  -alias gv \
  -keyalg RSA -keysize 2048 -validity 10000
```

```properties
# keystore.properties
storeFile=gv.jks
storePassword=your-store-password
keyAlias=gv
keyPassword=your-key-password
```

## Build

```bash
make build      # compile debug APK
make run        # build + install + launch on connected device
make log        # stream logcat filtered to this app
make clean      # wipe build artifacts
make release    # compile and install release APK on connected device
make uninstall  # remove app from device
make devices    # list connected ADB devices
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Testing

```bash
make test            # instrumented tests on connected device
./gradlew test       # unit tests (no device needed)
```

Instrumented tests live in `app/src/androidTest/java/com/gv/app/`.
Unit tests live in `app/src/test/java/com/gv/app/`.

A pre-commit hook runs `make test` automatically on every commit. Hooks are tracked in
`.githooks/` — activate them once after cloning:

```bash
make hooks
```

### Current tests

| File | Test | Type |
|------|------|------|
| `MainActivityTest.kt` | `mainScreen_showsHabitsTitle` | Instrumented |
| `NotificationSchedulerTest.kt` | 5 scheduling / timing tests | Unit |

## Features

- [Habits](docs/habits.md)
- [Daily notification](docs/notifications.md)
