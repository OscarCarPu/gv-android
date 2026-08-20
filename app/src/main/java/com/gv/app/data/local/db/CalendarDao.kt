package com.gv.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    // --- calendars ---

    @Query("SELECT * FROM calendar_calendar ORDER BY accountEmail, isPrimary DESC, summary")
    fun calendars(): Flow<List<CalendarEntity>>

    @Upsert
    suspend fun upsertCalendars(rows: List<CalendarEntity>)

    @Query("DELETE FROM calendar_calendar")
    suspend fun deleteAllCalendars()

    // --- events ---

    /**
     * Everything overlapping `[fromMs, toMs)`. One predicate for every row because a zero-length
     * event is stored a millisecond long — see [CalendarEventEntity.endsAtMs].
     */
    @Query(
        "SELECT * FROM calendar_event WHERE startsAtMs < :toMs AND endsAtMs > :fromMs " +
            "ORDER BY startsAtMs, instanceId",
    )
    fun eventsInRange(fromMs: Long, toMs: Long): Flow<List<CalendarEventEntity>>

    @Upsert
    suspend fun upsertEvents(rows: List<CalendarEventEntity>)

    /**
     * Clears a window before its fetched contents are written back, so an event deleted or moved
     * elsewhere does not survive as a ghost. Uses the same overlap predicate as the read, so the
     * rows removed are exactly the rows the fetch was able to replace.
     */
    @Query("DELETE FROM calendar_event WHERE startsAtMs < :toMs AND endsAtMs > :fromMs")
    suspend fun deleteEventsInRange(fromMs: Long, toMs: Long)

    /** Used when a calendar is hidden or stops syncing: its events are no longer fetched. */
    @Query("DELETE FROM calendar_event WHERE calendarId = :calendarId")
    suspend fun deleteEventsOfCalendar(calendarId: Int)

    /** Used when an account is disconnected: gv-api drops its calendars and events server-side. */
    @Query("DELETE FROM calendar_event WHERE accountId = :accountId")
    suspend fun deleteEventsOfAccount(accountId: Int)

    @Query("DELETE FROM calendar_event")
    suspend fun deleteAllEvents()
}
