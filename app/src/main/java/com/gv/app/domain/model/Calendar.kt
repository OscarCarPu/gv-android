package com.gv.app.domain.model

/**
 * Calendar DTOs. Mirrors `gv-api/internal/calendar/dto.go`; snake_case so Gson's defaults map
 * the JSON as-is, like the rest of the API's domains.
 *
 * The API is a mirror of the user's Google calendars and Google stays the source of truth:
 * reads come from the mirror, writes go to Google first. Recurrence is expanded server-side,
 * so what arrives from `GET /calendar/events` is already occurrences — this app never reads an
 * RRULE to work out when something happens.
 */

/** One calendar's sync state, for the operational rows in the calendars sheet. */
data class CalendarSyncState(
    val has_sync_token: Boolean = false,
    val last_sync_at: String? = null,
    val last_full_sync_at: String? = null,
    val last_sync_error: String? = null,
    val watch_active: Boolean = false,
    val watch_expires_at: String? = null,
)

data class GoogleCalendar(
    val id: Int,
    val account_id: Int,
    val account_email: String,
    val account_status: String,
    val google_calendar_id: String,
    val summary: String,
    val description: String = "",
    val time_zone: String = "",
    /** What to paint with: the user's override, else the colour gv assigned. */
    val color: String,
    /**
     * Google's own colour. Kept for reference only and deliberately never painted with: every
     * primary calendar comes back as the same pale cyan, so it identifies nothing.
     */
    val background_color: String = "",
    val foreground_color: String = "",
    val access_role: String = "",
    val writable: Boolean = false,
    val is_primary: Boolean = false,
    val sync_enabled: Boolean = false,
    val visible: Boolean = true,
    /** Gone from Google. The row and its events stay, so a view does not empty out silently. */
    val deleted: Boolean = false,
    val sync: CalendarSyncState = CalendarSyncState(),
)

data class CalendarAccount(
    val id: Int,
    val email: String,
    val label: String = "",
    val color: String = "",
    /** `connected`, `needs_reauth` (only a person can fix it) or `revoked`. */
    val status: String,
    val calendars: Int = 0,
    val last_sync_at: String? = null,
    val last_sync_error: String? = null,
    val created_at: String = "",
)

data class EventAttendee(
    val email: String,
    val display_name: String? = null,
    val optional: Boolean? = null,
    val response_status: String? = null,
    val self: Boolean? = null,
    val organizer: Boolean? = null,
)

data class EventReminderOverride(val method: String, val minutes: Int)

data class EventReminders(
    val use_default: Boolean = true,
    val overrides: List<EventReminderOverride>? = null,
)

/**
 * One event, or one occurrence of a recurring series.
 *
 * [instance_id] is the only handle a client may use to edit or delete: `"12"` for a plain event
 * or the series as a whole, `"12@2026-08-20T07:00:00Z"` for one occurrence, where the suffix is
 * that occurrence's **original** start. An override can move an occurrence to another day, and
 * the slot it came from is the only stable name it has — so references are passed back verbatim
 * and never assembled here.
 */
data class CalendarEvent(
    val instance_id: String,
    val event_id: Int,
    val calendar_id: Int,
    val account_id: Int,
    val account_email: String,
    val calendar_name: String,
    val color: String,
    val google_event_id: String = "",
    val summary: String = "",
    val description: String = "",
    val location: String = "",
    val status: String = "",
    /** `default`, or a kind Google generates: `birthday`, `fromGmail`, `workingLocation`. */
    val event_type: String = "",
    val all_day: Boolean = false,
    val starts_at: String,
    val ends_at: String,
    val time_zone: String = "",
    /**
     * All-day events only, and what they must be placed by: an all-day event is a date, not an
     * instant. [starts_at]/[ends_at] are also filled — midnight to midnight in [time_zone] — but
     * calendars disagree about that zone (Google reports some as UTC and some as Europe/Madrid),
     * so rendering those instants in the phone's zone spreads a one-day event over two local
     * days. [end_date] is exclusive, as in Google.
     */
    val start_date: String? = null,
    val end_date: String? = null,
    val recurring: Boolean = false,
    /** Absent rather than null when empty, so the API omits it entirely. */
    val recurrence: List<String>? = null,
    val is_exception: Boolean = false,
    val original_starts_at: String? = null,
    /** False for read-only calendars, parked accounts, and the kinds Google generates itself. */
    val editable: Boolean = true,
    val organizer_email: String? = null,
    val attendees: List<EventAttendee>? = null,
    val reminders: EventReminders? = null,
    val transparency: String? = null,
    val visibility: String? = null,
    val html_link: String? = null,
    val hangout_link: String? = null,
    val created_by_gv: Boolean = false,
    val updated_at: String? = null,
)

/** `attendees`/`reminders` are omitted: Google's defaults are what an event created here wants. */
data class CreateEventRequest(
    val calendar_id: Int,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val all_day: Boolean? = null,
    /** RFC3339, or `YYYY-MM-DD` when [all_day]. */
    val starts_at: String,
    val ends_at: String? = null,
    val time_zone: String? = null,
    val recurrence: List<String>? = null,
    val send_updates: String? = null,
)

/**
 * A patch: an absent field is left alone, which is exactly what Gson does with a null one.
 *
 * An empty [recurrence] list is not absent — it is how a series is turned back into a single
 * event, so it must survive serialisation as `[]`.
 */
data class UpdateEventRequest(
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val all_day: Boolean? = null,
    val starts_at: String? = null,
    val ends_at: String? = null,
    val time_zone: String? = null,
    val recurrence: List<String>? = null,
    /** `instance`, `following` or `all`. See [EventScope]. */
    val scope: String? = null,
    val send_updates: String? = null,
)

data class MoveEventRequest(
    val calendar_id: Int,
    val send_updates: String? = null,
)

data class MoveEventResult(
    val event: CalendarEvent,
    /**
     * True when the move crossed accounts: Google can only move within one, so the event was
     * recreated on the destination and removed from the source. Its id changed and the old
     * reference is dead.
     */
    val recreated: Boolean = false,
)

/** Local preferences only — none of this is sent to Google. */
data class UpdateCalendarRequest(
    val sync_enabled: Boolean? = null,
    val visible: Boolean? = null,
    val color_override: String? = null,
)

data class UpdateCalendarAccountRequest(
    val label: String? = null,
    val color: String? = null,
)

data class AuthUrlResponse(val url: String)

data class SyncResult(
    val calendars: Int = 0,
    val upserted: Int = 0,
    val deleted: Int = 0,
    val errors: List<String>? = null,
)

/** What a change to a recurring series touches. */
object EventScope {
    /** That occurrence only. The default when the reference names one. */
    const val INSTANCE = "instance"

    /** That occurrence and every later one; the series is split and the id changes. */
    const val FOLLOWING = "following"

    /** The whole series, or a plain event. The only scope that may change the rule. */
    const val ALL = "all"
}

/** Who Google mails about a change. The API defaults to `none`, and so does this app. */
object SendUpdates {
    const val NONE = "none"
    const val EXTERNAL_ONLY = "externalOnly"
    const val ALL = "all"
}
