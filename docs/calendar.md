# Calendar

Source: `app/src/main/java/com/gv/app/ui/calendar/`, `app/src/main/java/com/gv/app/domain/model/Calendar.kt`,
`app/src/main/java/com/gv/app/data/repository/CalendarRepository.kt`

Client for the `gv-api` `/calendar/events` endpoints. An event is a **name and a time range**;
gv is the only calendar interface (nothing syncs with Google). Month / week / day views, event
CRUD, and "Create plan" on an event. Mirrors gv-web's `/calendar`.

## Source chips

The header switches what the views draw (remembered in SharedPreferences `gv_calendar`):

- **Events**: events only; one with a plan block linked to it carries a check.
- **Plans**: plan blocks only.
- **Both**: events without a plan plus every plan block. An event that has a plan is drawn
  **once, as its plan block**; tapping it opens the event. Unlinked plan blocks are not editable
  here (Tasks owns them).

`buildItems` (CalendarUtils) is the pure rule, pinned by `CalendarUtilsTest`.

## Rules that bite

- **A timed event is a real instant**: convert the picked wall clock with the phone's offset
  (`localToApiInstant`), not by stamping it as UTC the way a task's `due_at` wants.
- **A plan block links to an event by `event_ref` = the event id** as a string. Moving the event
  moves the block (server side); deleting the event clears the link and keeps the block.
- **Writes are followed by re-reading the range**, never by patching local state.
- Events are cached in Room (`calendar_event`) so the calendar opens offline; plan blocks are
  not cached (offline, only events show). Writes are refused offline by `OnlineGate`.

## Create plan

`PlanFromEvent.kt` / `PlanFromEventSheet`: port of gv-web's wizard. The task (existing or new) is
written first, then the event's times if they changed, then the block; a task made for a plan
that then fails is deleted again.

## Not ported / removed

Calendars, accounts, colours, all-day events, recurrence, location/notes and the live SSE stream
no longer exist in the API. The side panel (Tune button) shows the next seven days of free time.
