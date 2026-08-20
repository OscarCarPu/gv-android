package com.gv.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached calendar list. Everything the grid needs to paint an event (colour, name) and the
 * sheet needs to manage it (writable, sync state) comes from here, so the calendar still
 * renders and reads correctly with no connection.
 */
@Entity(tableName = "calendar_calendar")
data class CalendarEntity(
    @PrimaryKey val id: Int,
    val accountId: Int,
    val accountEmail: String,
    val accountStatus: String,
    val summary: String,
    val timeZone: String,
    /** The colour to paint with, as the API resolved it (override, else gv's assigned entry). */
    val color: String,
    val accessRole: String,
    val writable: Boolean,
    val isPrimary: Boolean,
    val syncEnabled: Boolean,
    val visible: Boolean,
    val deleted: Boolean,
    val lastSyncAt: String?,
    val lastSyncError: String?,
    val watchActive: Boolean,
)

/**
 * One cached occurrence, keyed by the reference the API hands out — `"12"` for a plain event,
 * `"12@2026-08-20T07:00:00Z"` for one occurrence of a series. Recurrence is expanded server
 * side, so a series occupies as many rows here as it has occurrences in the fetched window.
 *
 * [startsAtMs] / [endsAtMs] exist because the range query has to happen in SQL and an RFC3339
 * string cannot be compared there: the API formats instants in whatever zone the calendar uses,
 * so `"2026-08-20T09:00:00+02:00"` and `"2026-08-20T08:00:00Z"` are the same moment and sort
 * differently. The ISO strings are kept alongside for display and for sending back.
 */
@Entity(
    tableName = "calendar_event",
    indices = [Index("startsAtMs"), Index("endsAtMs"), Index("calendarId")],
)
data class CalendarEventEntity(
    @PrimaryKey val instanceId: String,
    val eventId: Int,
    val calendarId: Int,
    val accountId: Int,
    val accountEmail: String,
    val calendarName: String,
    val color: String,
    val googleEventId: String,
    val summary: String,
    val description: String,
    val location: String,
    val status: String,
    val eventType: String,
    val allDay: Boolean,
    val startsAt: String,
    val endsAt: String,
    val startsAtMs: Long,
    /**
     * Always strictly greater than [startsAtMs]. A zero-length event is stored as one
     * millisecond long so a single overlap predicate covers every row — otherwise a point in
     * time matches no range and the event silently never appears.
     */
    val endsAtMs: Long,
    val timeZone: String,
    /** All-day events only, and what they are placed by. [endDate] is exclusive. */
    val startDate: String?,
    val endDate: String?,
    val recurring: Boolean,
    /** The raw rule lines as JSON, so a hand-written rule survives an edit untouched. */
    val recurrenceJson: String?,
    val isException: Boolean,
    val originalStartsAt: String?,
    val editable: Boolean,
    val organizerEmail: String?,
    val attendeesJson: String?,
    val htmlLink: String?,
    val createdByGv: Boolean,
)
