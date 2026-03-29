# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GV-Android is the Android client for gestor-vida, a personal life management system for tracking habits, tasks, and finances. It's a single-module Kotlin app using Jetpack Compose, targeting SDK 35.

## Build & Development Commands

All common operations are available via Makefile:

```bash
make build          # assembleDebug
make run            # build + install + adb reverse tcp:8080 + launch
make release        # assembleRelease + install release APK
make install        # build + adb install debug APK
make test           # ./gradlew connectedDebugAndroidTest (instrumented, requires device/emulator)
make clean          # ./gradlew clean
make log            # adb logcat filtered to app PID
make hooks          # set git hooks path to .githooks/ (pre-commit runs tests)
```

Direct Gradle: `./gradlew assembleDebug`, `./gradlew test` (unit tests), `./gradlew connectedDebugAndroidTest` (instrumented).

## Environment Setup

- `.env` — dev BASE_URL (http://localhost:8080/), used by debug builds
- `.env.prod` — production BASE_URL, used by release builds
- `keystore.properties` + `gv.jks` — release signing config (both gitignored)
- BASE_URL is injected at build time via `buildConfigField`, not at runtime

## Architecture

**MVVM + Clean Architecture layers** under package `com.gv.app`:

- **data/api/** — `ApiService` (Retrofit interface, 30+ suspend endpoints), `RetrofitClient` (singleton with OkHttp auth interceptor)
- **data/local/** — `TokenManager` (SharedPreferences-based JWT storage exposing `StateFlow<String?>`)
- **domain/model/** — Data classes for all entities (Habit, Task, Project, Todo, TimeEntry, etc.)
- **domain/usecase/** — Stub (not yet implemented)
- **ui/** — Compose screens organized by feature: `login/`, `habits/`, `tasks/`, `finance/`, `navigation/`, `components/`
- **notification/** — AlarmManager-based daily reminder system (11 PM) with boot receiver

**Key patterns:**
- **Auth routing**: `TokenManager.tokenFlow` drives login vs main screen display in MainActivity
- **Optimistic updates**: Habits and Todos update UI immediately, revert on API error
- **Activity-scoped TimerViewModel**: Shared across tab screens (Tasks/Habits)
- **OkHttp interceptor**: Auto-injects Bearer token, clears token on 401 (triggers logout)
- **Two-step login**: Password → TOTP 2FA, managed by LoginViewModel state machine

## Testing

- **Framework**: JUnit 4 + MockK + Coroutines Test + Compose UI Test
- **Unit tests**: `app/src/test/java/`
- **Instrumented tests**: `app/src/androidTest/java/`
- Notification testing via adb: `adb shell am broadcast -a com.gv.app.ACTION_DAILY_ALARM -n com.gv.app.debug/com.gv.app.notification.NotificationReceiver`

## Navigation

Bottom tab navigation between Tasks and Habits screens, defined in `ui/navigation/AppNavigation.kt`. Notification tap opens the Habits "Set All" wizard via intent extra `EXTRA_OPEN_WIZARD`.

## Custom Theme

Dark theme with custom `MaterialTheme` colors defined in `MainActivity.kt`. `AppColors` object provides semantic colors (success, warning, danger, muted).
