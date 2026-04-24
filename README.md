# gv-android

Android interface for [gestor-vida](../gestor-vida) — a personal life management system.

**Current state:** skeleton. Login (password + 2FA) → empty Home placeholder. Feature screens (habits, tasks, finance) have been stripped and will be rebuilt on top of this skeleton, styled per [`docs/UI_STYLE_GUIDE.md`](docs/UI_STYLE_GUIDE.md) (tokens extracted from gv-web).

## Stack

- **Kotlin** + **Jetpack Compose** — UI
- **Retrofit + OkHttp** — REST API communication with the gestor-vida backend
- **Clean Architecture** — data / domain / ui layer separation

## Project structure

```
app/src/main/java/com/gv/app/
├── data/
│   ├── api/          # ApiService (Retrofit), RetrofitClient (OkHttp + auth interceptor)
│   └── local/        # TokenManager (SharedPreferences-backed JWT)
├── domain/
│   └── model/        # Auth data classes: LoginRequest, TwoFactorRequest, TokenResponse, ErrorResponse
├── notification/     # Skeleton plumbing only — no active alarm scheduled at startup
├── ui/
│   ├── login/        # LoginScreen + LoginViewModel (password + 2FA)
│   └── navigation/   # AppNavigation (Login → Home NavHost with Home placeholder)
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

## Versioning

`version.properties` at the repo root holds `versionCode` and `versionName`. `make release` bumps `versionCode` automatically before building, so every release APK replaces the prior install. Commit the updated `version.properties` alongside the release build.

## Build

```bash
make build      # compile debug APK
make run        # build + install + launch on connected device
make log        # stream logcat filtered to this app
make clean      # wipe build artifacts
make release    # bump versionCode, compile and install release APK on connected device
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

No tests currently exist; the harness (JUnit 4 + MockK + Coroutines Test + Compose UI Test) is wired in Gradle ready for features to land.

## Features

- [Authentication](docs/authentication.md) — password + 2FA login

## Development Approach

The foundation of this project — the structure, basic patterns, and integrations — was built manually. After that, all visual design and code is done by [Claude Code](https://claude.com/claude-code).
