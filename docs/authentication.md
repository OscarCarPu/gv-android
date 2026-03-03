# Authentication

Source: `app/src/main/java/com/gv/app/ui/login/`, `app/src/main/java/com/gv/app/data/local/TokenManager.kt`

Two-step JWT authentication (password + TOTP 2FA) matching the gv-api backend.
The app shows the login screen when no token is stored and redirects to the
habits screen after successful authentication.

---

## Auth flow

```
POST /login       { "password": "..." }              → { "token": "<tmp 5min>" }
POST /login/2fa   { "token": "...", "code": "..." }  → { "token": "<full 24h>" }
Protected requests:  Authorization: Bearer <full-token>
```

---

## Components

| File | Responsibility |
|------|---------------|
| `domain/model/Models.kt` | `LoginRequest`, `TwoFactorRequest`, `TokenResponse`, `ErrorResponse` data classes |
| `data/local/TokenManager.kt` | Persists JWT in SharedPreferences; exposes `tokenFlow: StateFlow<String?>` for reactive UI switching |
| `data/api/ApiService.kt` | `login()` and `login2fa()` Retrofit endpoints returning `Response<TokenResponse>` |
| `data/api/RetrofitClient.kt` | OkHttp interceptor that injects `Authorization: Bearer` header and clears token on 401 |
| `ui/login/LoginViewModel.kt` | Drives the two-step login state machine |
| `ui/login/LoginScreen.kt` | Password form and 6-digit TOTP form composables |
| `MainActivity.kt` | Initializes `TokenManager`, observes `tokenFlow`, routes between `LoginScreen` and `HabitsScreen` |

---

## Token management

`TokenManager` wraps `SharedPreferences` (file: `gv_auth`, key: `token`).

- `saveToken(token)` — persists and updates `tokenFlow`
- `clearToken()` — removes token and updates `tokenFlow`
- `tokenFlow` — seeded from persisted value on construction; drives the screen switch in `MainActivity`

The auth interceptor in `RetrofitClient` automatically:
- Adds `Authorization: Bearer <token>` to all requests except `/login` and `/login/2fa`
- Clears the token on any 401 response from a protected endpoint, which triggers automatic redirect to the login screen via `tokenFlow`

---

## Login screen states

```
Idle ──submitPassword──→ Loading ──success──→ AwaitingTwoFactor
                                  ──error───→ Error

AwaitingTwoFactor ──submitTwoFactorCode──→ Loading ──success──→ Success (token saved, UI switches)
                                                   ──error───→ AwaitingTwoFactor (with errorMessage, temp token preserved)
```

| State | UI |
|-------|-----|
| `Idle` | Password field (auto-focused) + Login button |
| `Loading` | Spinner |
| `Error(message)` | Error text + password form for retry |
| `AwaitingTwoFactor(tempToken, errorMessage?)` | 6-digit numeric input (auto-focused) + Verify button + optional inline error |
| `Success` | No-op; `MainActivity` observes `tokenFlow` and switches to `HabitsScreen` |

---

## Token persistence

- Kill and reopen the app → goes directly to `HabitsScreen` if a token is stored
- Token expiry → backend returns 401 → interceptor clears token → `tokenFlow` emits null → `LoginScreen` shown

---

## Environment setup

| File | BASE_URL | Purpose |
|------|----------|---------|
| `.env` | `http://localhost:8080/` | Local Docker API (dev) |
| `.env.prod` | `http://gv-api.lab-ocp.com/` | Production API |

The `Makefile` `run` target runs `adb reverse tcp:8080 tcp:8080` before launching,
so the phone's `localhost:8080` routes to the dev machine's Docker API.

---

## Testing

```bash
# Unit tests (no device needed)
./gradlew testDebugUnitTest --tests "com.gv.app.ui.login.LoginViewModelTest"

# Instrumented test (device required)
./gradlew connectedDebugAndroidTest
```

The `LoginViewModelTest` covers:
- Password submit success / HTTP error / network error
- 2FA submit success / error (preserves temp token) / no-op when not in correct state
- Correct request payloads via `slot<T>()`
- `clearError` behavior for both password and 2FA steps
