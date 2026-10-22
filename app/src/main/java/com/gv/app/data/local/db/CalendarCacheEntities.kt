package com.gv.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One cached calendar event.
 *
 * [startsAtMs] / [endsAtMs] exist because the range query has to happen in SQL and an RFC3339
 * string cannot be compared there: the API may format an instant in any offset, so
 * `"2026-08-20T09:00:00+02:00"` and `"2026-08-20T08:00:00Z"` are the same moment and sort
 * differently. The ISO strings are kept alongside for display and for sending back.
 */
@Entity(
    tableName = "calendar_event",
    indices = [Index("startsAtMs"), Index("endsAtMs")],
)
data class CalendarEventEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val startsAt: String,
    val endsAt: String,
    val startsAtMs: Long,
    /**
     * Always strictly greater than [startsAtMs]. A zero-length event is stored as one
     * millisecond long so a single overlap predicate covers every row — otherwise a point in
     * time matches no range and the event silently never appears.
     */
    val endsAtMs: Long,
)
