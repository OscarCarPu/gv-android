# Calendar

Source: `app/src/main/java/com/gv/app/ui/calendar/`, `app/src/main/java/com/gv/app/domain/model/Calendar.kt`,
`app/src/main/java/com/gv/app/data/repository/CalendarRepository.kt`,
`app/src/main/java/com/gv/app/data/api/CalendarStream.kt`

Read-and-write client for the `gv-api` `/calendar/*` endpoints: a view over the API's mirror of
the user's Google calendars, with month / week / day views, event CRUD, per-calendar visibility
and sync, and Google account management.

**The API owns everything that matters** — the mirror, the sync with Google, recurrence
expansion, and the rules about what may be written. Google stays the source of truth behind it:
reads come from the mirror, writes go to Google first and are stored only once it accepts them.
This client renders occurrences and collects edits, and in particular **never expands a
recurrence rule itself** — `GET /calendar/events` already returns occurrences.

The feature lives under the **Calendar** bottom-nav tab in `HomeScreen`, which is now six tabs
wide. It is absent from the `lights` flavour, like every tab but Lights.

---

## What's IN

- **Month** — a dot grid (colour per calendar) with the selected day's agenda list beneath it.
  Tapping a day moves the selection; tapping the day header opens the full day view.
- **Week and Day** — one time grid, a day being the same grid with one column: 24 hour rows, a
  sticky all-day band, overlapping events laid out in lanes, a ticking "now" line, and
  tap-an-hour-to-create.
- **Event create / edit / delete**, including the recurring-series scopes (`instance`,
  `following`, `all`) and the recurrence presets.
- **Move an event between calendars**, with the cross-account "it was recreated" case reported.
- **Read-only events** open as facts rather than a form: read-only calendars, parked accounts,
  and the kinds Google generates itself (`birthday`, `fromGmail`, `workingLocation`).
- **Per-calendar visibility and sync**, both server-side preferences, so every device agrees.
- **Accounts**: connect (browser consent), reconnect when Google rejects the grant, rebuild
  (resync), disconnect.
- **Live updates** over `GET /calendar/stream`, and **offline reading** from the Room cache.

## What's OUT

| Web feature | Why not here |
|---|---|
| `GET /calendar/sync/status` operational view (recent runs, poll interval, webhook health) | A dashboard. What a phone needs from it — an account that needs reconnecting, a calendar whose watch is dead, a sync error — is already on the calendars sheet, per row |
| Attendee editing | Typing guest lists on a phone is a desktop job; existing attendees are shown, and the notify-guests choice is offered whenever an event has any |
| Reminder overrides | Created events take Google's defaults; changing them is rare and fiddly |
| Per-calendar colour override (`color_override`) | Picking a hex on a phone to fix a colour you will see on a laptop |
| Account label / colour editing | Same |
| Custom recurrence building (`INTERVAL`, `UNTIL`, `COUNT`) | An existing custom rule is preserved untouched (see below), but composing one belongs on desktop |

---

## Components

| File | Responsibility |
|------|---------------|
| `domain/model/Calendar.kt` | DTOs: `GoogleCalendar`, `CalendarSyncState`, `CalendarAccount`, `CalendarEvent`, `EventAttendee`, `EventReminders`, `Create/UpdateEventRequest`, `MoveEventRequest`/`Result`, `UpdateCalendarRequest`, `SyncResult`, plus the `EventScope` / `SendUpdates` constant sets. snake_case to match the API JSON via Gson defaults. |
| `data/api/ApiService.kt` | Retrofit endpoints under `/calendar/*` — see API contract below. |
| `data/api/IsoTime.kt` | `parseInstantOrNull`, the one place that reads an instant off the wire. |
| `data/api/CalendarStream.kt` | The SSE reader for `/calendar/stream`, with reconnection and a connected/disconnected signal. |
| `data/local/db/CalendarCacheEntities.kt`, `CalendarDao.kt` | The read cache: `calendar_calendar` and `calendar_event`, range-queried by epoch millis. |
| `data/repository/CalendarRepository.kt` | Online-first store. Cache reads, range refetch, calendar preferences, account management, event writes. |
| `ui/calendar/CalendarUtils.kt` | The pure logic: range maths, day placement, lane layout, recurrence presets, colour ink, instant conversion. Unit-tested. |
| `ui/calendar/CalendarViewModel.kt` | `state: StateFlow<CalendarUiState>`, `accounts`, `toast`, `openUrl`. Owns the range and the stream subscription. |
| `ui/calendar/CalendarScreen.kt` | Header (title, prev/next, live dot, mode chips, Today, calendars button), the view host, the FAB and the sheets. Also `openInBrowser`. |
| `ui/calendar/CalendarViews.kt` | `MonthView` (grid + agenda) and `TimeGridView` (week and day). |
| `ui/calendar/EventSheet.kt` | Create/edit/read-only sheet for one event or occurrence. |
| `ui/calendar/CalendarsSheet.kt` | Calendars grouped by account: visibility dot, sync switch, account actions. |
| `ui/calendar/CalendarFields.kt` | The form controls those two sheets are built from. |

---

## Non-obvious rules

These are the places a calendar renders plausibly while being wrong. Each has a test in
`app/src/test/java/com/gv/app/ui/calendar/CalendarUtilsTest.kt`.

- **An all-day event is a date, not an instant.** Place it by `start_date` / `end_date`, never by
  `starts_at` / `ends_at`. Those instants are midnight in the *calendar's* zone and calendars
  disagree about which that is — Google reports some as `UTC` and some as `Europe/Madrid` — so
  rendering them in the phone's zone spreads a one-day event across two local days, which reads
  as a duplicate.
- **An all-day end is exclusive**, as in Google and in the API. The form collects the *last day
  covered* and converts both ways, so a one-day event is `starts_at = D`, `ends_at = D+1`.
- **A timed event is a real instant.** Use `localToApiInstant`, which converts the picked wall
  clock with the phone's offset. `TasksUtils.localDateTimeToIsoUtc` looks similar but is for a
  task's `due_at`; the difference is invisible in Madrid winter and moves every appointment by an
  hour or two the rest of the year.
- **Instants arrive with an offset, not always `Z`.** `Instant.parse` accepts only `Z`, so
  everything goes through `parseInstantOrNull`. A failed parse means the event silently vanishes
  from its day rather than erroring.
- **An occurrence is addressed by `instance_id`** (`12@2026-08-20T07:00:00Z` — the *original*
  start of that occurrence, not where an override moved it). It is passed straight back to
  `PATCH` / `DELETE` and never assembled by hand.
- **The reference is sent unencoded.** Retrofit's normal `@Path` leaves `@` and `:` alone in a
  path segment, which is what gv-api's parser expects; the API's own e2e test uses that form.
  Percent-encoding them would break it — chi routes on the *raw* path and hands the handler the
  encoded segment, where `strings.Cut(ref, "@")` finds no separator and the id fails to parse.
- **A rule that bounds itself is left alone.** Only a plain `FREQ=…` maps onto a preset; anything
  with `COUNT`, `UNTIL` or `INTERVAL` shows as "Custom rule (kept as it is)" and is sent back
  verbatim, because offering it as "Weekly" would drop the clause that makes it specific.
- **The recurrence rule belongs to the series**, so it can only be changed with `scope=all`; the
  field is disabled otherwise. `instance` and `following` require an occurrence reference, and
  the API answers `400` rather than guessing.
- **After a `following` split the event id changes** — the occurrence now belongs to a new
  series. After a cross-account move the event is recreated and the old reference is dead. Both
  are why a write is followed by re-reading the range rather than patching local state.
- **Which ink goes on a chip is decided per colour**, by WCAG relative luminance. Calendar colours
  come from the API and can be anything; hardcoding white text works until someone pins a pale
  one and the title vanishes.
- **`background_color` is never painted with.** It is Google's own value and identifies nothing —
  every primary calendar comes back as the same pale cyan. `color` is what to use.

---

## Views

`MonthView` departs from gv-web on purpose. The web puts text chips in month cells; a phone column
is about fifty points wide, where a title clips to two letters and reads as noise. So the grid
carries up to four colour dots per day and the **agenda list below it** carries the detail — which
also means any day of the month is one tap from its plan, instead of a drill-in and a way back.

`TimeGridView` serves week and day. Both precompute their per-day placement with `remember`,
keyed on `(events, days)`: placing an event parses its instants, a month grid asks 42 times, and
the time grid re-composes every time the "now" line ticks.

Overlapping events are walked in start order and each takes the first lane whose previous event
has finished — enough to stop two appointments at the same hour hiding one another, without the
interval-graph colouring a desktop calendar does. Boxes are clamped to the day (a multi-day event
draws to the column edges) and never shorter than 24 minutes, so a zero-length event stays
tappable.

---

## Data layer

Online-first with a Room read cache, like habits, tasks and routes — and unlike lights, which
keep none. A stale bulb state invites tapping a control that cannot run; a stale calendar is still
the answer to "what have I got on today", which is exactly the question worth answering in a
tunnel.

- **`calendar_event` is keyed by `instance_id`**, so a series occupies as many rows as it has
  occurrences in the fetched window.
- **`startsAtMs` / `endsAtMs` exist because the range query happens in SQL** and an RFC3339 string
  cannot be compared there: `2026-08-20T09:00:00+02:00` and `2026-08-20T08:00:00Z` are the same
  moment and sort differently. The ISO strings are kept alongside for display and for sending
  back.
- **A zero-length event is stored one millisecond long**, so one overlap predicate covers every
  row. A point in time overlaps no range, and the event would otherwise never appear.
- **Every fetch and cache-replace is padded by a day at each end.** An all-day event's instants
  can be a day away from the phone's local day, and views filter by local day afterwards, so
  fetching exactly the visible range drops the events at its edges.
- **A window is cleared before its fetched contents are written back**, in one transaction, so an
  event deleted in Google or moved out of the window does not survive as a ghost. Only a
  *successful* fetch touches the cache — a failed refresh leaves the previous copy readable.
- **Accounts are not cached**: the sheet that shows them can only act online anyway.
- **Writes are refused offline** by `OnlineGate`, and the refusal is spoken through the toast flow.
  A tap that silently does nothing reads as a bug.

---

## Live updates

`GET /calendar/stream` is server-sent events. It says only *that* something changed; the client
refetches the range it is showing, so a live update and a freshly opened tab take one code path
and cannot drift out of step with the server the way an incremental patch stream can. A burst of
notifications is coalesced over 400 ms.

`CalendarStream` is hand-rolled rather than `EventSource`-shaped: the framing is three lines long,
and the one thing a library would add — reconnection — has to be gated on connectivity here
anyway. Unlike a browser, this client **can** send the bearer token as a header, so it talks to
the API directly and needs no proxy (gv-web has to route the same stream through its own server,
because `EventSource` cannot set headers).

Two details that are easy to get wrong:

- **The stream needs its own OkHttp client** (`RetrofitClient.streamClient`). The read timeout is
  disabled, because a healthy stream is silent for 25 seconds between keep-alives; and the logging
  interceptor is left out, because at `BODY` level it buffers the whole response before handing it
  on, which for an endless body never returns.
- **The subscription lives as long as the screen is in front, not as long as the ViewModel.** The
  ViewModel is scoped to the Activity and outlives the tab, so `LifecycleResumeEffect` starts and
  stops it. Coming back also re-reads the range and the calendar list, which covers whatever moved
  while the stream was down.

The header carries a live/not-live dot. It is worth the line because a push channel can die
quietly, and nothing else on screen would say so.

---

## Adding a Google account

`POST /calendar/accounts/auth-url` returns Google's consent URL, opened in a Custom Tab. There is
**no callback into the app**: Google redirects to gv-api's `/calendar/google/callback`, which
redirects on to the gv web app. So the flow finishes in the browser, and this side finds out by
re-reading accounts and calendars when the screen resumes — which the calendars sheet says out
loud, because otherwise it looks like nothing happened.

When Google is not configured on the server at all, the domain still mounts: accounts and
calendars come back empty, reads work, and anything needing Google answers `503`, surfaced here as
"Google is not configured on the server."

Holiday, birthday and week-number calendars arrive with `sync_enabled: false`, because gv-api
cannot bound the initial import by date (Google forbids `timeMin` next to a sync token) and a
decade of public holidays would be imported whole. That is why the sheet labels the sync switch
rather than hiding it.

---

## API contract

| Method | Endpoint | Used for |
|---|---|---|
| `GET` | `/calendar/events?from&to&visible_only` | The visible range, occurrences already expanded |
| `GET` | `/calendar/events/{ref}` | One event or occurrence (wired, not currently called) |
| `POST` | `/calendar/events` | Create |
| `PATCH` | `/calendar/events/{ref}` | Edit, with `scope` and `send_updates` |
| `DELETE` | `/calendar/events/{ref}?scope&send_updates` | Delete |
| `POST` | `/calendar/events/{ref}/move` | Move between calendars |
| `GET` | `/calendar/calendars` | The calendar list and its sync state |
| `PATCH` | `/calendar/calendars/{id}` | `visible`, `sync_enabled` |
| `GET` | `/calendar/accounts` | The accounts sheet |
| `POST` | `/calendar/accounts/auth-url` | Google consent URL |
| `POST` | `/calendar/accounts/{id}/resync` | Rebuild one account's mirror |
| `DELETE` | `/calendar/accounts/{id}` | Disconnect |
| `POST` | `/calendar/sync[?calendar_id]` | Sync now |
| `GET` | `/calendar/stream` | Change notifications |

Not wired: `GET /calendar/sync/status`, `PATCH /calendar/accounts/{id}` (label/colour), and the two
public endpoints Google itself reaches (`/calendar/google/callback`, `/calendar/google/webhook`).

Statuses worth handling, and how they read: `403` read-only calendar → "That calendar is read-only
in Google."; `409 refetch and retry` → "This event changed in Google while you were editing it.";
`409 account needs to be reconnected` → "That Google account needs to be reconnected."; `503` →
"Google is not configured on the server."

---

## Future work

- **Swipe to change period.** The other tabs use a `HorizontalPager`; here it fights the time
  grid's vertical scroll and the week's horizontal density, so navigation is buttons for now.
- **A widget or notification for the next event.** The data is already cached locally, which is
  most of the work.
- **Attendee editing**, if inviting people from the phone turns out to matter.
- **`sync/status` as a debug screen**, behind `BuildConfig.DEBUG`, if a sync problem ever needs
  diagnosing away from a laptop.
