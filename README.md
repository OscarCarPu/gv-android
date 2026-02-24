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
│   ├── api/        # Retrofit service interfaces
│   └── local/      # Room database, DAOs, entities
├── domain/
│   ├── model/      # Shared models: Habit, Task, Transaction
│   └── usecase/    # Business logic, one class per use case
├── ui/
│   ├── components/ # Reusable Compose widgets
│   ├── habits/     # Habit tracking screens
│   ├── tasks/      # Task management screens
│   └── finance/    # Finance screens (API-backed)
└── MainActivity.kt # App entry point
```

## Requirements

- Android SDK (API 35), build-tools 36.1.0
- `ANDROID_HOME` pointing to the SDK root
- ADB for device deployment

## Build

```bash
make build      # compile debug APK
make run        # build + install + launch on connected device
make log        # stream logcat filtered to this app
make clean      # wipe build artifacts
make release    # compile release APK
make uninstall  # remove app from device
make devices    # list connected ADB devices
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Testing

UI tests use the **Jetpack Compose test framework** with **kotlin-test** and run as instrumented tests directly on a connected device or emulator via ADB.

```bash
make test       # run all instrumented tests on connected device
```

Tests live in `app/src/androidTest/java/com/gv/app/`. Because they are instrumented, a device must be connected (`make devices` to verify) before running.

A pre-commit hook runs `make test` automatically on every commit. Hooks are tracked in `.githooks/` — activate them once after cloning:

```bash
make hooks
```

### Current tests

| File | Test | Description |
|---|---|---|
| `MainActivityTest.kt` | `mainScreen_showsHabitsTitle` | Asserts the "Habits" top-bar title is visible on launch |
