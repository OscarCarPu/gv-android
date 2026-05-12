# Tasks

Source: `app/src/main/java/com/gv/app/ui/tasks/`, `app/src/main/java/com/gv/app/domain/model/Task.kt`, `app/src/main/java/com/gv/app/data/api/ApiService.kt`

Read-and-write client for the `gv-api` `/tasks/*` and `/plan/*` endpoints. The mobile feature is deliberately a **focused subset** of the gv-web `/tasks` page — same entities and API contract, but the desktop's heavy editing affordances (plan-block editor, dependency selector, time-history charts, agenda sheet, project create) are out of scope. The phone is optimized for "what should I do right now?" and "I'm doing this right now" use cases.

The feature lives under the **Tasks** bottom-nav tab in `HomeScreen`.

---

## What's IN (mobile-appropriate)

- **Three sub-tabs**: Today, Due, Projects.
- **Compact running-timer card** at the top, with elapsed time, comment field, Stop and Cancel.
- **Today/Week progress bars** vs the user's daily/weekly target seconds.
- **Today's plan blocks** (read-only) — see the time-of-day schedule for today; tap a linked block to open the task.
- **Tasks by due date** with day dividers and a priority filter (≤1 / ≤2 / ≤3 / ≤4 / All). Each row has Start, Finish-or-Renew, and a per-row Timer button (label flips to "Assign" when a timer is already running).
- **Active project tree** (collapsible) — tap a leaf task to open detail; project nodes don't navigate, they expand/collapse.
- **Task detail bottom sheet** — full edit of name, description, due, type (Standard/Continuous/Recurring), recurrence (when applicable), priority (1-5 chips); Start/Finish/Renew button; todos (add/check/delete); deps/blocks shown as **read-only badges**; Delete with confirm.
- **Task create FAB** — name + project picker + due + type + recurrence + priority + "Start now" checkbox.
- **Timer lifecycle** — start (POST), restore on app open (GET active), reassign current entry to another task, stop with optional comment, cancel (delete the entry).

## What's OUT (better on desktop)

| Web feature | Why we skip it on mobile |
|---|---|
| Plan block create/edit | Precise time-block picking on a phone is awkward; users plan on desktop |
| `DepSelector` + reverse-sync (`Depends on` / `Blocks` editing) | Multi-select with subtree filter is fiddly on touch; deps are usually set up on desktop |
| Project create + project metadata edit | Hierarchy bootstrapping is a desktop "setup" job |
| Time history modal (chart by day/week/month) | Chart-heavy; the desktop already has a good view |
| Agenda right sheet | Calendar-style timeline; redundant with the Plan view |
| Manual time-entry inputs (the two TimePickers under the timer) | Backfilling old entries is rare on phone |
| Pace tooltip | Hover UX, useless on touch |
| `listProjectsFast` autocomplete inside `DepSelector` | Only needed if the deps editor exists |
| `/tasks/time-entries` history list and `/tasks/time-entries/history` | Used only by the time-history chart |
| `getProjectChildren` / project detail page | No project navigation on mobile beyond the active tree |

The endpoints for these features remain available server-side; if a mobile use case appears we can light them up incrementally.

---

## Components

| File | Responsibility |
|------|---------------|
| `domain/model/Task.kt` | DTOs: `TaskByDueDateResponse`, `TaskFullResponse`, `TaskResponse`, `ActiveTreeNode`, `TodoResponse`, `TimeEntryResponse`, `ActiveTimeEntryResponse`, `TimeEntrySummaryResponse`, `PaceBreakdown`, `PlanTodayResponse`, `PlanBlockResponse`, `ProjectListItem`, plus `Create*` / `Update*` request bodies. snake_case to match the API JSON via Gson defaults. |
| `data/api/ApiService.kt` | Retrofit endpoints under `/tasks/*` and `/plan/*` — see API contract below. Note: task / todo / time-entry updates use **PATCH** (matching the backend), unlike money which uses PUT. |
| `ui/tasks/TasksViewModel.kt` | `state: StateFlow<TasksUiState>` (Loading / Loaded(TasksData) / Error). `timer: StateFlow<TimerState>` driven by a 1-second tick. `editingDetail: StateFlow<TaskFullResponse?>` for the open detail sheet. All mutations call `refresh()` (no optimistic UI). |
| `ui/tasks/TasksScreen.kt` | The single-screen entry. Top: `TimerCard`. Below: tab bar (Today / Due / Projects) + content. A FAB opens `TaskCreateSheet`; stop button opens `StopTimerDialog`. |
| `ui/tasks/TaskSheets.kt` | `TaskDetailSheet` (full edit), `TaskCreateSheet` (new task), plus reusable `TypeChip`, `PrioritySelector`, `DateField`, `ProjectDropdown`, `GvField`, `TodoRow`. |
| `ui/tasks/TasksUtils.kt` | Locale-pinned formatters, `parseIso`, `localDateTimeToIsoUtc`, `formatRelativeDay`, `formatDurationShort`, `formatHhMmSs`, `statusLabel`, `priorityColor`, `taskTypeColor`, `buildRecurringDueAt`, `isOverdue`. |

---

## Sub-tabs

### Today

The default landing view, optimized for the morning glance.

1. `ProgressSummary` — two thin progress bars (today vs daily target, week vs weekly target). Bar color shifts at 5/6 of target (warning) and 11/12 (success), matching the web's thresholds.
2. `PlanBlockRow` list — read-only blocks for today. Time range (`HH:mm–HH:mm`) on the left, label/task on the right. Strikethrough when `task_finished_at != null`. Tap a linked block → open the task detail sheet.
3. Tasks due today or overdue (subset of `byDueDate` filtered to `date_key <= today_key`).

### Due

Full `tasksByDueDate` list with **day dividers** between dates (`Today`, `Yesterday`, `Tomorrow`, `EEE, d MMM`). Priority filter chips at the top. Overdue rows get a red border accent. Each row has:

- Name + Blocked icon (when `blocked = true`) + priority pill.
- Project name (if any).
- Status badge + due-date pill + time-spent pill.
- A two-button action row: Start / Finish/Renew on the left, Timer/Assign on the right. Buttons are disabled when `blocked = true`.

### Projects

The `getActiveTree` payload flattened with the same `ancestorHasMore` pattern used in the money categories view — except here projects are foldable (chevron), and tapping a task leaf opens the detail sheet. Projects themselves don't navigate; project create/edit lives on desktop.

---

## Timer

The active time entry is the single source of truth for "what's running right now". The view model:

- On every `refresh()` calls `getActiveTimeEntry`. If present, copies it to `_timer.active` and starts a 1s tick that recomputes `elapsedSeconds = now - started_at`.
- `startOrAssignTimer(taskId)` — POSTs `createTimeEntry` if nothing is running, otherwise PATCHes the existing entry's `task_id`. This matches the web's "Iniciar"/"Asignar" semantics.
- `stopTimer(comment)` — PATCHes `finished_at = now()` plus the comment, then `refresh()`.
- `cancelTimer()` — DELETEs the entry (use case: started by mistake), then `refresh()`.
- `updateTimerComment(comment)` — debounced 500ms PATCH from inline edits in the timer card.

The `TimerCard` collapses to a one-line "No timer running" strip when idle.

When the app is killed and reopened mid-timer, the running entry is rediscovered on the next `refresh()` call (which fires at ViewModel init) — no local persistence needed.

---

## Task mutations

| Action | Endpoint | Body |
|---|---|---|
| Start task | `PATCH /tasks/tasks/{id}` | `{ started_at: now }` |
| Finish task | `PATCH /tasks/tasks/{id}` | `{ finished_at: now }` |
| Renew recurring task | `PATCH /tasks/tasks/{id}` | `{ due_at: today + recurrence days }` (via `buildRecurringDueAt`) |
| Edit task | `PATCH /tasks/tasks/{id}` | `{ name, description, due_at, task_type, recurrence?, priority }` |
| Delete task | `DELETE /tasks/tasks/{id}` | — |
| Create task | `POST /tasks/tasks` | `{ name, project_id?, description?, due_at?, task_type?, recurrence?, priority? }` |

`UpdateTaskRequest` has all-nullable fields and Gson drops nulls by default, so a "set started_at only" call sends `{"started_at":"…"}` and the server treats it as a partial update.

### "Finish or Renew" routing

`finishTaskOrRenew(task)` in the ViewModel chooses one of two server calls based on the task:

- `task_type == "recurring" && recurrence != null` → renewal (`due_at = today + N days`), the task stays open and gets a new deadline.
- otherwise → completion (`finished_at = now`).

This matches the web logic in `+page.svelte` (`handleTaskToggle`) and `TaskBottomSheet.svelte`.

### Todos

`addTodo` / `toggleTodo` / `deleteTodo` operate against the open detail sheet. After each call we reload the full task to keep todo order and `is_done` flags in sync with the server.

---

## Locale handling

Same rules as the money feature:

- **Payload to API**: ISO-8601 UTC, locale `Locale.ROOT`. `localDateTimeToIsoUtc` converts a `LocalDateTime` (system zone) to a UTC `yyyy-MM-dd'T'HH:mm:ss'Z'` string.
- **Display**: `Locale.UK`. Day labels (`EEE, d MMM`), durations (`1h 23m`), HH:mm:ss for the timer (`formatHhMmSs`).

Dates entered in the form sheet use `LocalDate` (no time picker), normalized to noon UTC before sending — that avoids zone-boundary off-by-one and matches the desktop's habit of mostly using calendar-day granularity for due dates.

---

## API contract

```
GET    /tasks/tasks/by-due-date                → TaskByDueDateResponse[]
GET    /tasks/tree                             → ActiveTreeNode[]
GET    /tasks/tasks/{id}                       → TaskFullResponse
POST   /tasks/tasks                            → TaskResponse
PATCH  /tasks/tasks/{id}                       → TaskResponse
DELETE /tasks/tasks/{id}                       → 204
GET    /tasks/projects/list-fast               → ProjectListItem[]
POST   /tasks/todos                            → TodoResponse
PATCH  /tasks/todos/{id}                       → TodoResponse
DELETE /tasks/todos/{id}                       → 204
POST   /tasks/time-entries                     → TimeEntryResponse
PATCH  /tasks/time-entries/{id}                → TimeEntryResponse
DELETE /tasks/time-entries/{id}                → 204
GET    /tasks/time-entries/active              → ActiveTimeEntryResponse
GET    /tasks/time-entries/summary             → TimeEntrySummaryResponse
GET    /plan/today                             → PlanTodayResponse
```

Not wired (desktop-only on this client): `/tasks/projects` (list/get/get-children/create/update/delete), `/tasks/time-entries` (list + history), `/plan/blocks` (POST/PUT/DELETE), `/tasks/tasks/list-fast`.

---

## Future work

- **Boot-time notification when a task is due** — surface today's overdue count as a quick local notification at a configurable time. Reuses the dormant `notification/` skeleton.
- **Optional offline cache** — keep the last `getTasksByDueDate` snapshot so the Today tab renders before the network round-trip on reopens.
- **Per-task time-entry list** — show a task's recent timer entries inside the detail sheet (uses the existing `/tasks/tasks/{id}/time-entries` endpoint).
- **Light plan-block editing** — a single "shift this block by ±15min" gesture might be cheap to add and is useful on the go; full create/edit still belongs on desktop.
