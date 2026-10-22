package com.gv.app.data.repository

import androidx.room.withTransaction
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.parseInstantOrNull
import com.gv.app.data.local.db.CalendarDao
import com.gv.app.data.local.db.CalendarEventEntity
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.UpdateEventRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Calendar store over gv-api's events (a name and a time range).
 *
 * Online-first, like every other domain here: Room holds a read cache so the calendar still opens
 * with no connection, and writes are refused by [OnlineGate] rather than queued. After a write the
 * visible range is re-read by the caller instead of patched from the request.
 */
class CalendarRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: CalendarDao,
    private val gate: OnlineGate,
) : CacheRefresher {

    /** Cached events overlapping `[from, to)`. */
    fun eventsBetween(from: Instant, to: Instant): Flow<List<CalendarEvent>> =
        dao.eventsInRange(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    /**
     * Re-reads one window and replaces the cached rows in it. Only a successful fetch touches the
     * cache, so a failed refresh leaves yesterday's copy readable rather than blanking the screen.
     */
    suspend fun refreshRange(from: Instant, to: Instant): ApiResult<Unit> =
        when (
            val result = safeApiCall {
                api.getCalendarEvents(
                    from = RFC3339.format(from.atOffset(ZoneOffset.UTC)),
                    to = RFC3339.format(to.atOffset(ZoneOffset.UTC)),
                )
            }
        ) {
            is ApiResult.Success -> {
                val rows = result.data.mapNotNull { it.toEntityOrNull() }
                db.withTransaction {
                    // Clear first: an event deleted elsewhere would otherwise survive as a ghost.
                    dao.deleteEventsInRange(from.toEpochMilli(), to.toEpochMilli())
                    dao.upsertEvents(rows)
                }
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> result
        }

    suspend fun createEvent(request: CreateEventRequest): ApiResult<CalendarEvent> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createCalendarEvent(request) }
    }

    suspend fun updateEvent(id: Int, request: UpdateEventRequest): ApiResult<CalendarEvent> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateCalendarEvent(id, request) }
    }

    suspend fun deleteEvent(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCallNoBody { api.deleteCalendarEvent(id) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            // Already gone is the outcome the caller wanted.
            is ApiResult.Failure -> if (r.code == 404) ApiResult.Success(Unit) else r
        }
    }

    /** Background warm-up: keeps the month around today readable offline. */
    override suspend fun refresh() {
        runCatching {
            val now = Instant.now()
            refreshRange(now.minus(Duration.ofDays(14)), now.plus(Duration.ofDays(45)))
        }
    }

    private companion object {
        val RFC3339: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    }
}

/**
 * Null for an event whose start cannot be read, the one field the cache cannot work around:
 * without an instant there is no range to store it in.
 */
private fun CalendarEvent.toEntityOrNull(): CalendarEventEntity? {
    val startMs = parseInstantOrNull(start_time)?.toEpochMilli() ?: return null
    val endMs = parseInstantOrNull(end_time)?.toEpochMilli() ?: startMs
    return CalendarEventEntity(
        id = id,
        name = name,
        startsAt = start_time,
        endsAt = end_time,
        startsAtMs = startMs,
        endsAtMs = maxOf(endMs, startMs + 1),
    )
}

private fun CalendarEventEntity.toDomain() =
    CalendarEvent(id = id, name = name, start_time = startsAt, end_time = endsAt)
