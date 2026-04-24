# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GV-Android is the Android client for gestor-vida. The app is currently a **skeleton**: auth (login + 2FA) wired through to an empty "Home" placeholder. Feature screens (habits, tasks, finance) have been removed; they will be rebuilt on top of this skeleton. Single-module Kotlin app using Jetpack Compose, targeting SDK 35.

## Build & Development Commands

All common operations are available via Makefile:

```bash
make build          # assembleDebug
make run            # build + install + adb reverse tcp:8080 + launch
make release        # bumps versionCode in version.properties, assembleRelease, installs
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
- `version.properties` — tracked file holding `versionCode` / `versionName`. `make release` increments `versionCode` before building so each release APK replaces the previous install.
- BASE_URL is injected at build time via `buildConfigField`, not at runtime

## Architecture

**MVVM + Clean Architecture layers** under package `com.gv.app`:

- **data/api/** — `ApiService` (Retrofit interface, 2 auth endpoints: `login`, `login2fa`), `RetrofitClient` (singleton with OkHttp auth interceptor)
- **data/local/** — `TokenManager` (SharedPreferences-based JWT storage exposing `StateFlow<String?>`)
- **domain/model/** — Auth data classes only (`LoginRequest`, `TwoFactorRequest`, `TokenResponse`, `ErrorResponse`)
- **ui/** — Compose screens: `login/` (LoginScreen + LoginViewModel), `navigation/` (`AppNavigation.kt` — Login → Home NavHost with inline `HomeScreen` placeholder)
- **notification/** — Skeleton only: `NotificationHelper`, `NotificationScheduler`, `NotificationReceiver`. No alarm is scheduled at startup; the classes and manifest receiver entry are retained so a future feature can reactivate by calling `NotificationHelper.createChannel()` + `NotificationScheduler(ctx).scheduleDailyAlarm()`. Boot-time re-arming is not wired up — if a future alarm needs to survive reboot, re-add a `BootReceiver`.

**Key patterns:**
- **Auth routing**: `TokenManager.tokenFlow` drives navigation inside `AppNavigation`. On token change, the NavHost navigates between `login` and `home` routes.
- **OkHttp interceptor**: Auto-injects Bearer token, clears token on 401 (triggers logout)
- **Two-step login**: Password → TOTP 2FA, managed by `LoginViewModel` state machine

## Testing

- **Framework**: JUnit 4 + MockK + Coroutines Test + Compose UI Test (wired in Gradle; no tests currently exist under `app/src/test/` or `app/src/androidTest/`)

## Navigation

Two routes only: `login` → `home`, defined inline in `ui/navigation/AppNavigation.kt`. `HomeScreen` is a centered "Home" placeholder composable.

## Visual style

`docs/UI_STYLE_GUIDE.md` extracts the dark-theme visual tokens (color palette, typography scale, spacing, shape/elevation, motion, state deltas) from the sibling `gv-web` project. Consult it before building any new feature UI — it is the authoritative source for color/typography/spacing decisions on Android.

## Custom Theme

Dark theme with custom `MaterialTheme` colors defined in `MainActivity.kt`. `AppColors` object provides semantic colors (success, warning, danger, muted).
