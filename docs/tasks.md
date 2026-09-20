# Tasks

Source: `app/src/main/java/com/gv/app/ui/tasks/`, `domain/model/Task.kt` + `Plan.kt`, `data/repository/TaskRepository.kt` + `PlanRepository.kt`, `data/api/ApiService.kt`

Client for the `gv-api` `/tasks/*`, `/plan/*` and `/capacity/*` endpoints. The screen follows gv-web's `/tasks` page — same two tabs, same Due Soon ordering, same plan — and the flows a task goes through (start it, finish it, put a timer on it) are the ones the web has. Where the web is a wide page and the phone is not, the *layout* differs (one column, sheets instead of popovers); the logic does not.

The feature lives under the **Tasks** bottom-nav tab in `HomeScreen`.

Like the rest of the app it is **online-first, offline read-only**: lists render from a cached snapshot, every write goes straight to the server and is refused when offline, and after a write the state is re-read rather than patched.

---

## Layout

Top to bottom: a **Today | Projects** toggle, the **timer panel**, then the two tabs as swipeable pages. The panel is pinned above both tabs (the web shows it only on Today; on a phone a running timer you cannot see or stop from Projects is worse than the ten extra lines).

### Timer panel (`TimerPanel.kt`, `TaskPickerSheet.kt`)

- Row one: the pen (opens the task picker), the task and project name (tap → task detail), a chevron.
- Row two: the elapsed clock and **Start / Stop**.
- **Start with nothing running opens the picker**, and picking a task starts the timer on it. This is the one place the phone differs from the web's flow: the web can run a bare clock with no task, but here a timer only exists once the server has issued a time entry (see `TaskRepository`), so it always belongs to a task.
- Picking while a timer runs **re-points** the running entry at the new task (no time logged, just a different task).
- The chevron unfolds: comment (saved 500 ms after you stop typing, and only when it differs from what the server holds), the start time, Cancel (deletes the entry), Today / Week progress against the daily and weekly targets, and the Agenda.
- **The picker** is a searchable sheet grouped by project (`GET /tasks/tasks/list-fast`, read live each time it opens). A failed load says "Could not load tasks" with a Retry — it never reads as an empty account. The same picker serves the plan-block editor and the commitments form.

### Today tab

Due Soon on top, Today's Plan below it, in one scrolling list.

**Due Soon** (`DueSoon.kt`, `TaskBoard.kt`, `DueSoonSection.kt`) — ported from web's `dueSoonGrouping.ts` and `TaskBoard`:

- **Four tiers**: Overdue, Start Today, This Week, Later. A task is *overdue* when its date is before today; *Start Today* when it is due today **or** the API marked it `urgent`; *This Week* when its effective date is within seven days; otherwise *Later*. The **effective date** is `start_by` when the task has an estimate, else its due date; it is what This Week / Later sort by. Overdue and Start Today sort by the real due date. Priority breaks ties; undated tasks sort last within their tier.
- A task due today never leaves *Start Today* because of its estimate: the estimate only ever promotes a task *before* its due date.
- **Tiering happens before folding**, so an urgent task that plain date order would rank last is never cut by the fold. The list folds at **8** and "N more" unfolds 8 at a time (smaller than the usual 15/10: a Due Soon card carries badges and an urgency line).
- **Filters**: priority (All · ≤1 · ≤2 · ≤3 · ≤4) and project (includes sub-projects). Changing either resets the fold. The number beside the title counts tasks due today or earlier.
- **The urgency line** ("6h left · should've started 2d ago") explains *why* a task is urgent instead of leaving it to colour. It needs `urgent`, `remaining_hours` and `start_by`, all computed by the API from the estimate and free capacity — the client only renders them. Urgent rows get a warning border; overdue rows red, and a row that is both stays red.
- **Row actions**: Start (→ Done, or Renew for a recurring task) and the timer buttons. With no timer running the timer button is one play button; with one running it is **Assign** (re-point the running entry) and **Stop Start** (finish it, begin a new one on this task). All disabled on a `blocked` task.
- A finished task disappears immediately (a `pending` set in the ViewModel hides it until the re-read confirms it) and comes back if the write fails. A renewed task stays: only its date moves.

**Today's Plan** (`PlanTimeline.kt`, `PlanViewModel.kt`, `PlanSection.kt`) — ported from web's `planOverlay.ts` and `PlanBoard`:

- **One agenda around a "now" line.** Everything before now is rebuilt from what *actually happened* (today's time entries); everything after is plain intent (the plan's blocks). Rows above the line: **actual** (a real entry, tied to the block that planned it when one matches — "27m / 1h", "12m short", "planned for 15:00" when it ran off-schedule), **rest** (a planned break nothing ran through), **skipped** (a block that did not happen: not done / done at another time / a break worked through), **gap** (past time nothing accounts for, over two minutes). Rows below: **planned**, the block in progress counting only what is left.
- **The estimate bar** is two-tone: solid is logged, lighter is projected if every remaining task block is honoured. It is derived from the timeline, not from the API's day summary, so the bar and the rows can never disagree. "Skipped" counts only genuinely untouched task time — a task moved to another hour, or a break worked through, is not a shortfall.
- **The free-time strip** shows the next seven days' free share of capacity; tight days turn amber, full days red. **Tap a day** to browse and edit that day's blocks (a plain list — no "now" line, no actual-vs-planned merge, which only mean something for today); the restore icon returns to today.
- **Blocks**: create and edit in `PlanBlockSheet` (a task's slot or free time; times are wall-clock on the block's own day, and an end of 00:00 means the end of that day), delete, and "clear future blocks". A planned row carries the same Start / Done / Renew + timer buttons as Due Soon. Tapping an *actual* row opens that time entry in the agenda's editor; a *running* one opens the timer panel instead, because closing a live entry by giving it an end time is not what a tap should do.
- **Recurring commitments** (`CommitmentsSheet`, from the pen beside the strip): weekly blocks (work, gym…) the API turns into plan blocks and counts against capacity. The task of an existing commitment cannot change — the API has no field for it — so editing shows it read-only.

The web's block-start **alarm** (Web Audio) has no Android counterpart yet.

### Projects tab (`ProjectsTab.kt`, `TaskTree.kt`)

The active tree with the web's priority filter (projects are always kept whatever their children's priorities). Projects start collapsed. A task row has Start / Done / Renew and the timer buttons; a project row has **+** (new task, pre-filled with that project) and **Done**. Project *create / edit* and the project detail page are not on Android.

---

## What is not on Android

| Web feature | Why |
|---|---|
| Time-history chart, money stats | Chart-heavy; deliberately out of scope |
| Dependency selector + reverse-sync | Multi-select with a subtree filter is fiddly on touch |
| Project create / edit, project detail page | Hierarchy set-up is a desktop job |
| Block-start alarm | Needs an Android notification/alarm design of its own |
| Manual time-entry row under the timer | The agenda's editor covers backfilling |
| Pace tooltip | Hover UX |

---

## Files

| File | Responsibility |
|------|---------------|
| `domain/model/Task.kt`, `Plan.kt` | DTOs. snake_case to match the API JSON via Gson defaults. `TaskByDueDateResponse` carries the urgency fields (`urgent`, `start_by`, `remaining_hours`, `estimate_hours`); decimals arrive as strings. |
| `data/repository/TaskRepository.kt` | Cached snapshot (due list, tree, summary, today's plan, **today's time entries**, **7-day free/busy**), the server-issued timer, and every task / project / entry write. |
| `data/repository/PlanRepository.kt` | Plan-block and commitment writes; other days and the commitment list are read live. Each write ends by asking `TaskRepository` to re-read. |
| `ui/tasks/TasksViewModel.kt` | `state` (Loading / Loaded: data + the filtered Due Soon view + the filtered tree), `timer`, detail sheet state, and every action. Failures come back on `toast`. |
| `ui/tasks/PlanViewModel.kt` | The plan: timeline, summary, selected day, and block / commitment writes. Rebuilds the timeline on every snapshot change and once a minute. |
| `ui/tasks/DueSoon.kt`, `TaskBoard.kt`, `TaskTree.kt`, `PlanTimeline.kt` | **Pure logic**, no Android types, unit-tested. |
| `ui/tasks/TasksScreen.kt` | The shell: toggle, timer panel, pager, and every sheet/dialog. |
| `ui/tasks/TodayTab.kt`, `DueSoonSection.kt`, `PlanSection.kt`, `ProjectsTab.kt`, `TaskRows.kt` | The lists and their rows. |
| `ui/tasks/TimerPanel.kt`, `TaskPickerSheet.kt`, `TimeEntrySheet.kt`, `AgendaSheet.kt` | Timer and time-entry editing. |
| `ui/tasks/PlanBlockSheet.kt`, `CommitmentsSheet.kt`, `TaskSheets.kt` | Editors: plan block, commitments, task detail / create. |
| `ui/tasks/TasksUtils.kt` | Locale-pinned formatters, `parseIso`, `localDateTimeToIsoUtc`, `statusLabel`, colours. |

Tests (`app/src/test/.../ui/tasks/`): `DueSoonTest`, `TaskBoardTest`, `TaskTreeTest`, `PlanTimelineTest`, `PlanEditingTest`. They pin the placements that render fine while being wrong — an urgent task under "later", a task worked at the wrong hour reported as "not done", a break counted twice, a timer started this second missing from the plan.

---

## Timer

The server is the only source of truth for "what is running". `TaskRepository` re-reads `GET /tasks/time-entries/active` after every timer write, and once on startup — an app killed and reopened mid-timer finds its entry again.

- `startOrAssignTimer(taskId)` — `POST` a new entry, or `PATCH` the running one's `task_id`.
- `stopAndStartTimer(taskId)` — stop (`finished_at = now`) then start; the row's **Stop Start**.
- `stopTimer(comment)`, `cancelTimer()` (deletes), `updateTimerComment`, `editActiveTimerStart`.
- After any timer or entry write, **today's entries and the summary are re-read** too, so the plan's "what I did" is right the moment a timer starts or stops.

## Task mutations

| Action | Endpoint | Body |
|---|---|---|
| Start task | `PATCH /tasks/tasks/{id}` | `{ started_at: now }` |
| Finish task | `PATCH /tasks/tasks/{id}` | `{ finished_at: now }` |
| Renew recurring task | `PATCH /tasks/tasks/{id}` | `{ due_at: today + recurrence days }` |
| Start / finish project | `PATCH /tasks/projects/{id}` | `{ started_at }` / `{ finished_at }` |
| Edit / delete / create task | `PATCH` / `DELETE` / `POST /tasks/tasks` | see `TaskSheets.kt` |

Clearable fields go through `PatchBody` so an explicit JSON `null` survives serialisation. **Finish or Renew** is one decision, made in `TaskRepository.finishOrRenew`: a recurring task with a `recurrence` is renewed (its date moves, it stays open), anything else is finished.

## Dates

`due_at` is a *conceptual date*, not an instant. Due Soon reads its first ten characters rather than converting a zone, and a picked due date is sent as noon UTC — a timezone conversion here shifts the task a day. Time entries and plan blocks are real instants: parse them with `parseInstantOrNull`, and convert a picked wall clock with `localDateTimeToIsoUtc`.

## API contract

```
GET    /tasks/tasks/by-due-date       GET    /tasks/tree             GET  /tasks/tasks/list-fast
GET    /tasks/tasks/{id}              POST   /tasks/tasks            PATCH|DELETE /tasks/tasks/{id}
PATCH  /tasks/projects/{id}           GET    /tasks/projects/list-fast
POST   /tasks/todos                   PATCH|DELETE /tasks/todos/{id}
POST   /tasks/time-entries            PATCH|DELETE /tasks/time-entries/{id}
GET    /tasks/time-entries            (today's entries — the plan's past half)
GET    /tasks/time-entries/active     GET    /tasks/time-entries/summary
GET    /plan/today                    GET    /plan/range?from&to     (another day)
POST   /plan/blocks                   PUT|DELETE /plan/blocks/{id}   DELETE /plan/blocks/future
GET|POST /plan/commitments            PUT|DELETE /plan/commitments/{id}
GET    /capacity/free-busy?from&to    (the free-time strip)
```
