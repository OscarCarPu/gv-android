# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GV-Android is the Android client for gestor-vida. The app currently contains: auth (login + 2FA) and a single **daily Spotify alarm** as the post-login home screen. Other feature screens (habits, tasks, finance) are intentionally deferred. Single-module Kotlin app using Jetpack Compose, targeting SDK 35.

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

- `.env` — dev config, used by debug builds. Keys:
  - `BASE_URL` (e.g. `http://localhost:8080/`)
  - `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `SPOTIFY_REDIRECT_URI` (see Spotify setup below)
- `.env.prod` — production config, used by release builds. Same keys as `.env`.
- `keystore.properties` + `gv.jks` — release signing config (both gitignored).
- `version.properties` — tracked file holding `versionCode` / `versionName`. `make release` increments `versionCode` before building so each release APK replaces the previous install.
- All env values are injected at build time via `buildConfigField`, not read at runtime.
- `app/libs/` — vendored third-party AARs (gitignored; `.gitkeep` committed). The Spotify App Remote SDK lives here.

### Spotify setup (one-time, per developer machine)

1. Create a Spotify Developer app at developer.spotify.com/dashboard.
2. Edit Settings → **Redirect URI**: `com.gv.app://spotify-callback`.
3. **Android Packages**: register both `com.gv.app` (release) and `com.gv.app.debug` (debug, auto-generated via `applicationIdSuffix`), each with their SHA-1 fingerprint:
   - Debug: `keytool -list -v -keystore ~/.config/.android/debug.keystore -alias androiddebugkey -storepass android` (or `~/.android/debug.keystore` on some systems)
   - Release: `keytool -list -v -keystore gv.jks -alias <alias>`
4. **User Management**: while the app is in Development Mode, the Spotify account used for testing must be explicitly listed here or auth will silently fail.
5. Download `spotify-app-remote-release-X.Y.Z.aar` from github.com/spotify/android-sdk/releases and drop into `app/libs/`. The fileTree dependency in `app/build.gradle.kts` picks it up automatically.
6. Fill `SPOTIFY_CLIENT_ID` (and optionally `SPOTIFY_CLIENT_SECRET`, unused by App Remote but plumbed for future Web-API flows) into `.env` / `.env.prod`.

## Architecture

**MVVM + Clean Architecture layers** under package `com.gv.app`:

- **data/api/** — `ApiService` (Retrofit interface, 2 auth endpoints: `login`, `login2fa`), `RetrofitClient` (singleton with OkHttp auth interceptor).
- **data/local/** — `TokenManager` (SharedPreferences-based JWT storage exposing `StateFlow<String?>`).
- **domain/model/** — Auth data classes only (`LoginRequest`, `TwoFactorRequest`, `TokenResponse`, `ErrorResponse`).
- **alarm/** — Daily alarm feature. `AlarmPreferences` (SharedPreferences-backed `StateFlow<AlarmConfig>` with hour/minute/enabled), `AlarmScheduler` (`AlarmManager.setExactAndAllowWhileIdle` chained day-to-day), `AlarmTriggerReceiver` (BroadcastReceiver: starts the service and re-arms next day), `SpotifyAlarm` (see below).
- **notification/** — Legacy skeleton (`NotificationHelper`, `NotificationScheduler`, `NotificationReceiver`) currently unused by any active feature. Retained only because the manifest receiver entry is still present; scheduled for removal.
- **ui/** — Compose screens: `login/` (LoginScreen + LoginViewModel), `alarm/` (AlarmScreen + AlarmViewModel), `navigation/` (`AppNavigation.kt` — Login ↔ Home NavHost where the `home` route renders `AlarmScreen`), `theme/` (see Theme).

**Key patterns:**
- **Auth routing**: `TokenManager.tokenFlow` drives navigation inside `AppNavigation`. On token change, the NavHost navigates between `login` and `home` routes.
- **OkHttp interceptor**: Auto-injects Bearer token, clears token on 401 (triggers logout).
- **Two-step login**: Password → TOTP 2FA, managed by `LoginViewModel` state machine.
- **Autosave alarm config**: Both the time picker and the enabled switch in `AlarmScreen` write to `AlarmPreferences` on every change — no save button. Toggling enabled also schedules/cancels the alarm via `AlarmScheduler`.
- **Single file per external integration**: `SpotifyAlarm.kt` is the *only* file that imports `com.spotify.*`. Callers outside that file do not reference Spotify types — they invoke the service via `startForegroundService(Intent(ctx, SpotifyAlarm::class.java))`. Apply the same rule to future SDKs (Slack, OAuth providers, etc.).

### Alarm feature

- One daily alarm. Time (hour + minute) and enabled flag are persisted in `gv_alarm` SharedPreferences.
- When enabled, `AlarmScheduler` arms an exact wake-up alarm at the next occurrence of that time. On fire, `AlarmTriggerReceiver` starts `SpotifyAlarm` as a foreground service and re-schedules for the next day (AlarmManager exact alarms don't auto-repeat).
- `SpotifyAlarm` is a `foregroundServiceType=mediaPlayback` Service. On start it: posts a silent low-priority "Alarm" notification (required on Android 14+), connects to Spotify App Remote with the build-time client id + redirect URI, plays the hardcoded "Boom Boom" playlist URI, and ramps `AudioManager.STREAM_MUSIC` volume from 5% to 70% in +5 steps every 40s (14 steps ≈ 9m 20s), then disconnects and stops itself.
- Permissions: `USE_EXACT_ALARM`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, plus the pre-existing `INTERNET` and `POST_NOTIFICATIONS`.
- Boot persistence is not wired. If the phone reboots before the alarm fires it will not re-arm — reopening the app is not sufficient either; toggling enabled off/on re-schedules. Adding a `BootReceiver` is the future fix.
- First-time Spotify authorization: the App Remote SDK may try to pop Spotify's own auth activity. On Android 14+ this is subject to Background Activity Launch restrictions — if the Spotify app has not been recently foregrounded, the prompt is blocked silently. For the very first connect, open the Spotify app (playing any song) before the alarm fires; thereafter the authorization is cached and the alarm works without UI interaction.

## Testing

- **Framework**: JUnit 4 + MockK + Coroutines Test + Compose UI Test (wired in Gradle; no tests currently exist under `app/src/test/` or `app/src/androidTest/`).

## Navigation

Two routes: `login` → `home`, defined in `ui/navigation/AppNavigation.kt`. The `home` route hosts `AlarmScreen`.

## Visual style

`docs/UI_STYLE_GUIDE.md` extracts the dark-theme visual tokens (color palette, typography scale, spacing, shape/elevation, motion, state deltas) from the sibling `gv-web` project. Consult it before building any new feature UI — it is the authoritative source for color/typography/spacing decisions on Android.

## Theme

Dark-only theme in `ui/theme/`:
- `Color.kt` — `GvColors` singleton holds every base palette token from the style guide (`Bg`, `BgLight`, `Surface`, `Text`, `TextMuted`, `Primary`, `Secondary`, `Success`, `Danger`, `Warning`, `Continuous`, `Recurring`, `Border`, `BorderLight`).
- `Type.kt` — `GvTypography` maps style-guide roles onto Material 3 slots; `TimerDisplay` is a mono tabular style for numeric displays.
- `Spacing.kt` — `Spacing` data class (`xxs`..`huge`) exposed via `LocalSpacing` composition local. Access in composables as `LocalSpacing.current.xl`.
- `Shape.kt` — `GvShapes` (3/8/12/16dp steps; pill uses `CircleShape` at call sites).
- `Theme.kt` — `GvTheme { … }` wraps `MaterialTheme` with the above and provides `LocalSpacing`. Applied once in `MainActivity.onCreate`.

Do not define ad-hoc `Color(0xFF…)` or literal dp values in screens — consume from `GvColors` / `LocalSpacing` / `MaterialTheme.typography`.
