package com.gv.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    /** Everything overlapping `[fromMs, toMs)`. One predicate for every row, see the entity. */
    @Query(
        "SELECT * FROM calendar_event WHERE startsAtMs < :toMs AND endsAtMs > :fromMs " +
            "ORDER BY startsAtMs, id",
    )
    fun eventsInRange(fromMs: Long, toMs: Long): Flow<List<CalendarEventEntity>>

    @Upsert
    suspend fun upsertEvents(rows: List<CalendarEventEntity>)

    /**
     * Clears a window before its fetched contents are written back, so an event deleted
     * elsewhere does not survive as a ghost. Same overlap predicate as the read.
     */
    @Query("DELETE FROM calendar_event WHERE startsAtMs < :toMs AND endsAtMs > :fromMs")
    suspend fun deleteEventsInRange(fromMs: Long, toMs: Long)
}
