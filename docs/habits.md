# Habits Screen

Source: `app/src/main/java/com/gv/app/ui/habits/`

---

## Overview

The Habits screen lets you log daily numeric values for each tracked habit.
It talks to the backend through `HabitsViewModel` and updates the list
optimistically — the UI reflects a change immediately without waiting for the
network round-trip.

---

## UI structure

```
HabitsScreen
├── TopAppBar ("Habits")
├── DateNavBar
├── Box (fills remaining space)
│   ├── CircularProgressIndicator   — while loading
│   ├── ErrorState + Retry button   — on error
│   └── HabitList (LazyColumn)
│       └── HabitRow × N
└── FloatingActionButton (pencil)   — opens SetWizardDialog
```

---

## Date navigation

The `DateNavBar` at the top of the screen lets you move between days.

| Control | Action |
|---------|--------|
| ← arrow | Step back one day |
| → arrow | Step forward one day |
| Refresh icon | Jump back to today (only visible when viewing a past date) |

---

## Per-habit controls (HabitRow)

Each habit is rendered as a `Card` with two interaction zones:

### Value display

Shows the current logged value for the selected date, formatted without
trailing zeros (e.g. `3` not `3.0`). Displays `—` when no value has been
logged yet.

### Quick-adjust buttons

- **-1** — decrement the current value by 1 and commit immediately.
- **+1** — increment the current value by 1 and commit immediately.

### Inline Set (2 interactions)

Tapping **Set** replaces the value display with a focused `OutlinedTextField`
inside the same card — no dialog, no navigation.

1. The keyboard opens automatically the moment the field enters composition.
2. Type the desired value (decimal input).
3. Commit with the keyboard **Done** key or the **✓** icon button.
   Cancel with the **✗** icon button.

If the typed text is not a valid number the commit is silently skipped and the
field stays open so the user can correct it.

#### State design notes

```
remember(habit.id, habit.logValue) { mutableStateOf(...) }
```

Two keys keep `input` and `editing` in sync with the list:

- `habit.logValue` — resets `input` whenever the backend confirms a new value
  (e.g. after an optimistic commit is acknowledged). This prevents the field
  from showing a stale number.
- `habit.id` — resets `editing` to `false` when the list recomposes and a
  different habit occupies the same slot (e.g. after a sort or filter change).

The `FocusRequester` is created once per composition of `HabitRow` (not keyed
on anything). The `LaunchedEffect(Unit)` inside the `if (editing)` branch fires
exactly once — when that branch first enters composition — which is the right
moment to request keyboard focus.

---

## Set All wizard (SetWizardDialog)

The pencil FAB in the bottom-right corner opens a step-through `AlertDialog`
that walks through every habit in the list one at a time.

### Dialog anatomy

```
Title:  "Set All (N / Total): Habit Name"
Body:   OutlinedTextField  (auto-focused)
Confirm button:  "Next"  →  "Done" on the last habit
Dismiss row:     [Skip]  [Cancel]
```

### Interactions per step

| Button | Effect |
|--------|--------|
| **Next / Done** | Parse input → commit if valid → advance to next habit (or close) |
| **Skip** | Advance without committing; current value unchanged |
| **Cancel** | Close wizard; already-committed steps are preserved |
| Keyboard **Next / Done** | Same as the Next / Done button |

### Auto-focus

Each step gets its own `FocusRequester` instance via `remember(step)`.
A `LaunchedEffect(step)` fires whenever `step` changes, requesting focus on
the new field. This ensures the keyboard stays open and moves to the right
field between steps without the user having to tap.

### State design notes

```
var input          by remember(step) { mutableStateOf(...) }
val focusRequester  = remember(step) { FocusRequester() }
```

Both are keyed on `step` so each habit starts with a clean text field
pre-populated from its current `logValue`, and the previous step's
`FocusRequester` is not reused (which would risk stale focus state).

`commitAndAdvance` only calls `onSet` when the input parses as a `Double`;
if the field is empty or malformed the step is skipped silently (same
behaviour as tapping Skip).

---

## ViewModel interface

`HabitsViewModel` exposes the following for this screen:

| Member | Type | Purpose |
|--------|------|---------|
| `uiState` | `StateFlow<HabitsUiState>` | Loading / Error / Success with habit list |
| `displayDate` | `StateFlow<String>` | Formatted date string shown in the nav bar |
| `isToday` | `StateFlow<Boolean>` | Controls visibility of the Refresh icon |
| `previousDay()` | fun | Step back one day |
| `nextDay()` | fun | Step forward one day |
| `returnToday()` | fun | Jump to today |
| `loadHabits()` | fun | Retry after an error |
| `decrementHabit(habit)` | fun | Subtract 1, optimistic update |
| `incrementHabit(habit)` | fun | Add 1, optimistic update |
| `setHabitValue(habit, value)` | fun | Set an arbitrary value, optimistic update |
