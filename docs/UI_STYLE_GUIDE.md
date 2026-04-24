# gv-android UI Style Guide

Visual tokens extracted from `gv-web` for re-use in the Android rewrite. Scope is **styling only** — colors, typography, spacing, shape, elevation, motion, iconography, state deltas. No layout, no page anatomy, no component composition.

`gv-web` is **single-theme dark**: no light variant, no media-query toggles, no theme switch. A future Android light mode is out of scope for this document — if needed later, values will have to be designed, not extracted.

All token values trace back to `gv-web/src/styles/*.css`. Citations are `file.css:line`.

---

## 1. Color palette

Defined in the Tailwind v4 `@theme` block at `app.css:6–30`.

### Base tokens

| Token | Hex / rgba | Role | Recommended M3 slot |
|---|---|---|---|
| `--color-bg` | `#0B0F1A` | App background (darkest) | `background` |
| `--color-bg-light` | `#141926` | Cards, sheets, modals, toasts (elevated surface) | `surface` |
| `--color-surface` | `#1A2033` | Declared but not referenced by any rule in the CSS files; reserve for a third elevation if ever needed | `surfaceVariant` |
| `--color-text` | `#E8ECF4` | Primary text | `onSurface` / `onBackground` |
| `--color-text-muted` | `#7B8BA5` | Secondary text, disabled text, muted icons | `onSurfaceVariant` |
| `--color-primary` | `#3B82F6` | Primary actions, links, focus ring, active nav, selected states | `primary` |
| `--color-secondary` | `#A78BFA` | Secondary accent; dependency badges/pills | `secondary` / `tertiary` |
| `--color-success` | `#34D399` | Success states, "finished" status, start button, success toast accent | — (custom `Success`) |
| `--color-info` | `#3B82F6` | Informational — identical value to `--color-primary` | same as `primary` |
| `--color-danger` | `#F87171` | Errors, delete, overdue, logout hover, error toast accent | `error` |
| `--color-warning` | `#FBBF24` | Warnings, priority P2, required flag, active streak | — (custom `Warning`) |
| `--color-continuous` | `#2DD4BF` | "Continuous" task-type badge | — (custom accent) |
| `--color-recurring` | `#F59E0B` | "Recurring" task-type badge | — (custom accent) |
| `--color-border` | `rgba(255,255,255,0.06)` | Subtle 1px dividers, default card/section border | `outlineVariant` |
| `--color-border-light` | `rgba(255,255,255,0.08)` | Slightly more prominent border (sheets, modals, login form) | `outline` |

Note on M3 mapping: the web palette does not track M3 semantic pairs (no explicit `onPrimary`, `primaryContainer`, etc.). For `primary`, `secondary`, and `danger/error`, use `Color.White` as the `onX` value — solid buttons set `text-white` explicitly (`components.css:37, 270, 278, 396`, `login.css:40`).

### State tint recipe

`gv-web` uses CSS `color-mix()` and Tailwind opacity modifiers to derive tinted surfaces and fills from the base palette rather than defining new tokens. In Compose, mirror with `Color.copy(alpha = …)`.

Observed opacity levels (exhaustive — do not round out to extra steps):

| Usage | Alpha(s) | Example source |
|---|---|---|
| Very subtle fill, hover hint | `0.04`, `0.05`, `0.08` | `tasks.css:157, 144, 163` |
| Tag/badge background | `0.10`, `0.15`, `0.20` | `components.css:27, 286, 331`; `habits.css:36` |
| Tag/badge hover, progress track | `0.25`, `0.30` | `tasks.css:480`; `components.css:296` |
| Border (colored) | `0.25` (via `border-current/25`), `0.30`, `0.35`, `0.40`, `0.50` | `components.css:8`; `tasks.css:144, 162, 172`; `components.css:198` |
| Focus ring / hover border | `0.40`, `0.45`, `0.55`, `0.60` | `components.css:398`; `tasks.css:539, 166, 176` |
| Solid-button hover | `0.80` | `components.css:37, 270, 278` |

Compose example:

```kotlin
object GvColors {
    val Primary      = Color(0xFF3B82F6)
    val Secondary    = Color(0xFFA78BFA)
    val Bg           = Color(0xFF0B0F1A)
    val BgLight      = Color(0xFF141926)
    val Surface      = Color(0xFF1A2033)
    val Text         = Color(0xFFE8ECF4)
    val TextMuted    = Color(0xFF7B8BA5)
    val Success      = Color(0xFF34D399)
    val Danger       = Color(0xFFF87171)
    val Warning      = Color(0xFFFBBF24)
    val Continuous   = Color(0xFF2DD4BF)
    val Recurring    = Color(0xFFF59E0B)
    val Border       = Color(0x0FFFFFFF)   // 6% white  → rgba(255,255,255,0.06)
    val BorderLight  = Color(0x14FFFFFF)   // 8% white  → rgba(255,255,255,0.08)
}

// State tints (mirror color-mix by copy(alpha = ...)):
val PrimaryTintBg   = GvColors.Primary.copy(alpha = 0.08f)   // task-item.today bg
val PrimaryTintHover = GvColors.Primary.copy(alpha = 0.12f)  // task-item.today hover bg
val DangerTintBorder = GvColors.Danger.copy(alpha = 0.40f)   // task-item.overdue border
```

---

## 2. Typography

### Font families

- Sans: **Inter**, fallback `system-ui`, `sans-serif` (`app.css:22`).
- Mono: **JetBrains Mono**, fallback `ui-monospace`, `monospace` (`app.css:23`).

Android recommendation: bundle Inter via `res/font/` (variable or 400/500/600/700) and JetBrains Mono similarly. If not bundled, fall back to `FontFamily.SansSerif` and `FontFamily.Monospace` — loss of consistency with web is accepted.

### Scale (observed)

Each entry below lists only combinations actually used in the CSS. Pixel values assume Tailwind defaults (`text-xs` = 12px/16px line-height, `text-sm` = 14px/20px, `text-base` = 16px/24px, `text-lg` = 18px/28px, `text-xl` = 20px/28px, `text-2xl` = 24px/32px, `text-3xl` = 30px/36px).

| Role | Size | Weight | Extras | M3 slot (suggested) | Source |
|---|---|---|---|---|---|
| Page heading | 30sp | 700 | — | `displaySmall` | `app.css:44` (`.container > h1`) |
| Form / modal title | 24sp | 700 | centered; also `tracking-widest` on login 2FA code (mono) | `headlineMedium` | `components.css:264`; `login.css:12, 35` |
| Section / card title | 18sp | 600 | — | `titleLarge` | `tasks.css:108, 116, 506`; `habits.css:28` |
| Nav / modal close glyph | 18sp | 700 | — | `titleLarge` (icon-only) | `components.css:32` |
| Date-nav label | 18sp | 500 | centered | `titleMedium` | `app.css:62` |
| Info-value numeric | 16sp | 600 | tabular | `titleSmall` | `tasks.css:375` |
| Body / list item name | 14sp | 700 | — | `bodyLarge` (bold variant) | `tasks.css:186` |
| Body / description | 14sp | 400 | muted color | `bodyMedium` | `tasks.css:198, 224`; `habits.css:40` |
| Button text | 14sp | 500 | — | `labelLarge` | `components.css:4` (`@utility btn`) |
| Input text | 14sp | 400 | — | `bodyMedium` | `tasks.css:300`; `login.css:27` |
| Nav link | 14sp | 600 | uppercase, `tracking-wide` | `labelLarge` | `components.css:20` |
| Small label / caption / badge | 12sp | 500 | — | `labelMedium` | `components.css:8` (`@utility status-badge`); `habits.css:32, 36, 70, 77` |
| Metadata / pill-time / agenda | 12sp | 400 | tabular, mono for numeric | `labelSmall` / `bodySmall` | `tasks.css:232, 255, 552, 610` |
| Show-more chevron | 10sp | — | — | (icon-only) | `tasks.css:136` |

Numeric-heavy text uses `font-mono` with `tabular-nums`:
- Timer display: 24sp bold mono tabular — `tasks.css:63`
- Login 2FA code input: 24sp bold mono tabular, `tracking-widest` — `login.css:35`
- Agenda/hour labels, entry times: 12sp mono tabular — `tasks.css:552, 566, 610, 614`
- Custom time input (pill): 14sp mono centered — `components.css:213`

Compose examples:

```kotlin
val GvTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

val TimerDisplay = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize = 24.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum", // tabular-nums
)
```

---

## 3. Spacing

Base unit: **4dp** (Tailwind's `1` = 0.25rem = 4px at 16-px root).

Observed multiples only (do not add steps not in the CSS):

| Scale name | Value (dp) | Tailwind equiv. | Typical use |
|---|---|---|---|
| `xxs` | 2 | `0.5` / `px-0.5`, `py-0.5` | Badge vertical padding (`components.css:8`) |
| `xs` | 4 | `1` | Icon-to-text gap, tight spacing (`components.css:16`) |
| `sm` | 6 | `1.5` | Dependency pills (`tasks.css:487`) |
| `md` | 8 | `2` | Standard gap, button icon+label, input vertical (`components.css:4`) |
| `mdx` | 10 | `2.5` | Time-input horizontal, small rounded pill (`components.css:210`; `tasks.css:633`) |
| `lg` | 12 | `3` | List-item gap, section gap (`tasks.css:122, 154`) |
| `xl` | 16 | `4` | Card/modal inner padding, content padding (`tasks.css:153`; `login.css:24`) |
| `xxl` | 20 | `5` | Button horizontal padding, card horizontal padding (`components.css:4`; `tasks.css:4`) |
| `xxxl` | 24 | `6` | Sheet inner padding, login form padding (`components.css:67`; `login.css:7`) |
| `huge` | 32 | `8` | Main content top padding (`app.css:49`) |

```kotlin
object Spacing {
    val xxs  = 2.dp
    val xs   = 4.dp
    val sm   = 6.dp
    val md   = 8.dp
    val mdx  = 10.dp
    val lg   = 12.dp
    val xl   = 16.dp
    val xxl  = 20.dp
    val xxxl = 24.dp
    val huge = 32.dp
}
```

---

## 4. Shape & elevation

### Corner radii

Only four distinct radii appear in the CSS.

| Name | Value | Tailwind | Use | Source |
|---|---|---|---|---|
| `xs` | 3dp | (raw CSS) | Checkbox only | `tasks.css:319` |
| `sm` | 8dp | `rounded-lg` | Buttons, inputs, list rows, toasts, pill-inputs, tag chips | `components.css:4`; `tasks.css:153`, `login.css:27` |
| `md` | 12dp | `rounded-xl` | Cards, modals, form containers, habit card, timer panel, right-sheet | `habits.css:9`; `components.css:255`; `tasks.css:4`; `login.css:7` |
| `lg-top` | 16dp top-only | `rounded-t-2xl` | Bottom sheet top corners | `components.css:83` |
| `pill` | fully rounded | `rounded-full` | Status badges, frequency badges, adjust buttons (+/-), knob, chips, progress track | `components.css:8`; `habits.css:18, 48`; `components.css:296` |

```kotlin
val GvShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    // pill: use CircleShape at call sites
)
```

### Borders

| Use | Spec | Source |
|---|---|---|
| Default card/divider | 1px solid `--color-border` (6% white) | `app.css:59`; `tasks.css:5, 105, 154, 409`; `habits.css:10` |
| Elevated container | 1px solid `--color-border-light` (8% white) | `components.css:84, 127, 257`; `login.css:8` |
| Input default | 1px solid `text-muted/20` | `tasks.css:300`; `login.css:27`; `components.css:161, 195` |
| Focus | `border-primary/50` + `ring-primary/30 ring-1` (≈ 1dp border + 1dp ring) | `components.css:198`; `tasks.css:300`; `login.css:27` |
| Error / invalid field | `border-danger` + `ring-danger/40 ring-1` | `components.css:397` |
| Checkbox | 2px solid `--color-text-muted` (unchecked) | `tasks.css:318` |
| Toast left accent | 3px left border in `success` or `danger` | `components.css:414, 418` |
| Colored tag/badge | `border border-current/25` (inherits text color at 25% alpha) | `components.css:8`; `habits.css:36`; `tasks.css:478, 491, 645` |
| Tree-child left rail | 2px solid `--color-primary` (left edge only) | `tasks.css:280` |

### Shadows / elevation

Five elevation "recipes" observed. All shadows are black-tinted except success/error notifications which tint their shadow with the accent color.

| Recipe | Value | Used on | Source |
|---|---|---|---|
| E1 — ambient card | `0 4px 20px rgba(0,0,0,0.30)` | Habit card hover | `habits.css:14` |
| E1 — panel | `0 4px 24px rgba(0,0,0,0.20)` | Timer panel | `tasks.css:6` |
| E2 — toast | `0 8px 32px rgba(0,0,0,0.40)` | Toast notification | `components.css:410` |
| E2 — notification card | `0 8px 24px rgba(0,0,0,0.30)` | In-app notification | `components.css:431` |
| E3 — sheet (bottom) | `0 -8px 40px rgba(0,0,0,0.40)` | Bottom sheet (shadow casts up) | `components.css:85` |
| E3 — sheet (right) | `-8px 0 40px rgba(0,0,0,0.40)` | Right sheet (shadow casts left) | `components.css:128` |
| E4 — modal / login | `0 24px 80px rgba(0,0,0,0.40–0.50)` | Modal card, login form | `components.css:258`; `login.css:9` |
| Tinted (success) | `0 8px 24px rgba(52,211,153,0.15)` | Success notification | `components.css:451` |
| Tinted (danger) | `0 8px 24px rgba(248,113,113,0.15)` | Error notification | `components.css:461` |

Android translation (rough, using `Modifier.shadow(elevation = …)` which derives blur/opacity from a single dp value):

| Recipe | Suggested dp |
|---|---|
| E1 | `4.dp` |
| E2 | `8.dp` |
| E3 | `12.dp` |
| E4 | `24.dp` |

For directional sheet shadows (E3), Compose's default shadow is omnidirectional — acceptable approximation; do not chase pixel parity. For tinted notification shadows, leave the shadow plain and use a border / background tint to read as "success/error" instead.

### Backdrop filter

`backdrop-filter: blur(8px)` is applied only to the notification card (`components.css:432`). Compose equivalent: `Modifier.blur(8.dp)` on the background layer (requires API 31+). If the minimum API is lower or behavior is inconsistent, skip the blur — it is a garnish, not load-bearing.

---

## 5. Motion

### Transition durations

Three durations span all UI transitions:

| Duration | Tailwind | Where |
|---|---|---|
| 150ms | default (unqualified `transition-colors`) | Most hover color swaps — `components.css:4, 20, 23, 32`; throughout |
| 150ms | `duration-150` | Agenda entry (border+bg+scale) — `tasks.css:570` |
| 200ms | `duration-200` | Timer-display hover, task item transitions, habit card transitions, checkbox animation hint — `tasks.css:66, 260`; `habits.css:9` |
| 300ms | `duration-300` | Progress bar fill, show-more pill — `components.css:300`; `tasks.css:128, 132` |

Compose:

```kotlin
val Motion = object {
    val fast   = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)
    val normal = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val slow   = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
}
```

### Keyframe animations

Observed entrance easing `cubic-bezier(0.16, 1, 0.3, 1)` is the Material "emphasized decelerate" shape — use a `CubicBezierEasing` mirror.

| Animation | Duration + easing | Use | Source |
|---|---|---|---|
| `slide-up` | 200ms `cubic-bezier(0.16, 1, 0.3, 1)` | Bottom sheet in | `components.css:86, 97` |
| `slide-down` | 150ms `ease-in` | Bottom sheet out | `components.css:93, 106` |
| `slide-in-right` (sheet) | 200ms `cubic-bezier(0.16, 1, 0.3, 1)` | Right sheet in | `components.css:129, 136` |
| `slide-in-right` (toast) | 200ms `ease-out` | Toast in (different keyframe redefined in `@keyframes` with opacity) | `components.css:411, 498` |
| `slide-out-right` | 150ms `ease-in` | Right sheet out | `components.css:132, 145` |
| `fade-in` | 150ms `ease-out` | Modal backdrop | `components.css:251, 478` |
| `scale-in` | 150ms `ease-out` | Modal card (0.95 → 1.0 + opacity) | `components.css:256, 487` |
| `spin` | 600ms linear, infinite | Loading spinner | `components.css:473, 509` |
| `notification-slide-in` | 300ms `cubic-bezier(0.16, 1, 0.3, 1)` | Notification in (400px + scale 0.9) | `components.css:433, 515` |
| `notification-fade-out` | 400ms `ease-in`, `1.6s` delay | Notification auto-dismiss | `components.css:433, 526` |
| `agenda-pulse` | 2000ms `ease-in-out`, infinite | Running-task indicator (0.7 ↔ 1.0 opacity) | `tasks.css:588, 617` |

Compose mapping:

```kotlin
val EmphasizedDecelerate = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

val SlideUp           = tween<IntOffset>(200, easing = EmphasizedDecelerate)
val SlideDown         = tween<IntOffset>(150, easing = EaseIn)
val FadeIn            = tween<Float>(150, easing = EaseOut)
val ScaleIn           = tween<Float>(150, easing = EaseOut)
val SpinnerRotation   = infiniteRepeatable(
    tween<Float>(600, easing = LinearEasing),
    repeatMode = RepeatMode.Restart,
)
val AgendaPulse       = infiniteRepeatable(
    tween<Float>(2000, easing = FastOutSlowInEasing),
    repeatMode = RepeatMode.Reverse,
)
```

---

## 6. Iconography & imagery

### Icon sizing

Icons are FontAwesome glyphs scaled by Tailwind text-size classes, not by dedicated icon sizes.

| Context | Size | Source |
|---|---|---|
| Show-more pill chevron | 10sp | `tasks.css:136` |
| Default inline icon | 12sp (`text-xs`) / 14sp (`text-sm`) | `tasks.css:136, 247`; throughout |
| Header / sheet-close / modal title icons | 18sp (`text-lg`) | `components.css:32, 63`; `components.css:437` |

No explicit stroke-width is set; all glyphs inherit font weight from surrounding text.

### Icon-button sizing (tap targets)

| Size (dp) | Tailwind | Use | Source |
|---|---|---|---|
| 28 × 28 | `w-7 h-7` | Habit history button | `habits.css:18` |
| 32 × 32 | `w-8 h-8` | Frequency toggle, sheet-close, toggle-switch knob | `components.css:63, 161, 356` |
| 36 × 36 | `w-9 h-9` | Cancel button | `components.css:45` |
| 40 × 40 | `w-10 h-10` | Habit +/- adjust buttons | `habits.css:48` |

All four are below Material's recommended 48dp minimum — when porting to touch, bump by padding the hit area rather than resizing the glyph.

### Image conventions

- Card image radii follow the container: `rounded-xl` (12dp) on elevated cards.
- No avatar sizing appears anywhere in the CSS — no convention to document.

---

## 7. State styling

Visual deltas only. `:hover` on the web → `pressed` / `focused` for touch (both apply on Android since there is no hover). `:focus-within` on containers → Compose's `FocusRequester`-driven focus state.

### Hover / pressed (touch)

- **Solid button** (primary/danger/success): background → same color at `alpha 0.80` (`components.css:37, 270, 278`).
- **Outline/muted button**: background `text-muted/15` → `text-muted/25` (`components.css:317`).
- **Cancel button**: base `danger/15` → `danger/30` (`components.css:45`).
- **Icon button**: color `text-muted` → `text` (or accent e.g. `primary`, `danger`) (`components.css:32, 63, 248`; `habits.css:19, 49`).
- **Nav link**: text `text-muted` → `text`; bg `transparent` → `white/5` (`components.css:23`).
- **List row neutral**: transparent border → `rgba(255,255,255,0.04)`; bg `bg` → `bg/80` (`tasks.css:156`).
- **List row "today"**: border 35% primary → 55% primary; bg 8%-primary-over-bg → 12%-primary-over-bg (`tasks.css:161`).
- **List row "overdue"**: border 40% danger → 60% danger; bg 8%-danger-over-bg → 12%-danger-over-bg (`tasks.css:171`).
- **Link text**: `primary` → `primary/80` (`tasks.css:211`).
- **Dependency pill**: `secondary/15` → `secondary/25` (`tasks.css:479`).

### Focus

- **Input**: border becomes `primary/50`, adds a 1dp ring at `primary/30` (`tasks.css:300`; `login.css:27`; `components.css:198`).
- **Comment input** (frameless): adds a 1dp ring at `primary/40` with no border (`tasks.css:38`).
- **Invalid field**: overrides the focus ring with `border-danger` + `ring-danger/40 ring-1` (`components.css:397`).

### Disabled

- **Buttons (primary/danger/outline) and inputs**: `opacity 0.50`, `cursor: not-allowed` (`components.css:40, 281, 321`; `login.css:30`).
- **Cancel button**: `opacity 0.30` (more muted than other disabled states) (`components.css:49`).
- **Submit button**: `opacity 0.50` **and** hover color is reverted to the base (disabled should not hint interactivity) (`login.css:43`).
- **Task selector disabled**: no opacity change, only cursor (`tasks.css:20`) — used when the data is legitimately empty rather than denied.

### Selected / active / checked

- **Status badge** (per state): `bg-{token}/20` + `text-{token}`, where `{token}` ∈ `primary` (started), `continuous`, `recurring`, `success` (finished) (`components.css:331-343`).
- **Nav link active**: `text-primary` + `bg-primary/10` (`components.css:26`).
- **Frequency-toggle active**: `bg-primary` + `border-primary` + `text-white` (`components.css:164`).
- **Create-mode active tab**: `bg-primary` + `text-white` (`components.css:396`).
- **Priority filter active**: `bg-primary/15` + `text-primary` + `border-primary/40` (`tasks.css:637`).
- **Checkbox checked**: `bg-primary` + `border-primary` + white SVG checkmark (`tasks.css:325`).
- **Streak (current, active)**: text `warning` (`habits.css:87`).

### Pressed (touch-only — no web analog)

Web doesn't define a `:active` pressed state distinct from hover. On Android, derive pressed from the hover delta but amplify: if hover tints background by `/10`, pressed tints by `/20`; if hover deepens border to 55% primary, pressed goes to 70%+ primary. Keep it visible but subordinate to the selected/active state.
