package com.gv.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.parseInstantOrNull
import com.gv.app.data.api.CalendarStream
import com.gv.app.data.api.CalendarStreamEvent
import com.gv.app.data.local.db.CalendarDao
import com.gv.app.data.local.db.CalendarEntity
import com.gv.app.data.local.db.CalendarEventEntity
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.domain.model.CalendarAccount
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CalendarSyncState
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.EventAttendee
import com.gv.app.domain.model.GoogleCalendar
import com.gv.app.domain.model.MoveEventRequest
import com.gv.app.domain.model.MoveEventResult
import com.gv.app.domain.model.SyncResult
import com.gv.app.domain.model.UpdateCalendarAccountRequest
import com.gv.app.domain.model.UpdateCalendarRequest
import com.gv.app.domain.model.UpdateEventRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Calendar store, over gv-api's mirror of the user's Google calendars.
 *
 * Online-first, like every other domain here, with two things worth knowing:
 *
 * 1. **Recurrence is never expanded on this side.** `GET /calendar/events?from&to` already
 *    returns occurrences, and an occurrence's [CalendarEvent.instance_id] is the only handle
 *    used to edit or delete it. Reading an RRULE here to work out when something happens would
 *    be a second implementation of the rule that could disagree with the server's.
 * 2. **After a write, the visible range is re-read** rather than patched from the request. Google
 *    is the source of truth and it rewrites what it is given — a "following" split moves the
 *    occurrence into a brand-new series with a different id, and a cross-account move recreates
 *    the event outright. Guessing either would leave the screen describing an event that no
 *    longer exists.
 *
 * Room holds a read cache so the calendar still opens with no connection; writes are refused
 * by [OnlineGate] rather than queued.
 */
class CalendarRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: CalendarDao,
    private val gate: OnlineGate,
    private val stream: CalendarStream,
) : CacheRefresher {

    // --- reads -------------------------------------------------------------------------

    fun calendars(): Flow<List<GoogleCalendar>> =
        dao.calendars().map { rows -> rows.map { it.toDomain() } }

    /** Cached occurrences overlapping `[from, to)`, widened so an all-day edge cannot fall out. */
    fun eventsBetween(from: Instant, to: Instant): Flow<List<CalendarEvent>> =
        dao.eventsInRange(padStart(from).toEpochMilli(), padEnd(to).toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    suspend fun refreshCalendars(): ApiResult<Unit> =
        when (val r = safeApiCall { api.listCalendars() }) {
            is ApiResult.Success -> {
                val rows = r.data.map { it.toEntity() }
                // The server list is the truth about which calendars exist, so swap the set.
                db.withTransaction {
                    dao.deleteAllCalendars()
                    dao.upsertCalendars(rows)
                }
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> r
        }

    /**
     * Re-reads one window and replaces the cached rows in it.
     *
     * The fetch is padded by a day at each end and so is the replace, for the same reason the
     * read is: an all-day event's instants are midnight in the *calendar's* zone, which can be
     * a day away from the phone's, and a view is filtered by local day afterwards. Fetching
     * exactly the visible range would drop the events at its edges.
     *
     * Only a successful fetch touches the cache — a failed refresh leaves yesterday's copy
     * readable rather than blanking the screen.
     */
    suspend fun refreshRange(from: Instant, to: Instant): ApiResult<Unit> {
        val fromPadded = padStart(from)
        val toPadded = padEnd(to)
        val result = safeApiCall {
            api.getCalendarEvents(
                from = RFC3339.format(fromPadded.atOffset(ZoneOffset.UTC)),
                to = RFC3339.format(toPadded.atOffset(ZoneOffset.UTC)),
                visibleOnly = true,
            )
        }
        return when (result) {
            is ApiResult.Success -> {
                val rows = result.data.mapNotNull { it.toEntityOrNull() }
                db.withTransaction {
                    // Clear first: an event deleted in Google, or moved out of this window,
                    // would otherwise survive as a ghost that only a reinstall removes.
                    dao.deleteEventsInRange(fromPadded.toEpochMilli(), toPadded.toEpochMilli())
                    dao.upsertEvents(rows)
                }
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> result
        }
    }

    /** Accounts are deliberately not cached: the sheet that shows them can only act online. */
    suspend fun listAccounts(): ApiResult<List<CalendarAccount>> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.listCalendarAccounts() }
    }

    // --- account management ------------------------------------------------------------

    /**
     * The Google consent URL for adding one account. Opened in the browser, which is where it
     * has to happen: the grant is Google's own screen, and its redirect lands on gv-api and then
     * on gv-web, not back here. Returns a `503` failure when the server has no Google
     * credentials configured at all.
     */
    suspend fun authUrl(): ApiResult<String> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCall { api.calendarAuthUrl() }) {
            is ApiResult.Success -> ApiResult.Success(r.data.url)
            is ApiResult.Failure -> r
        }
    }

    suspend fun renameAccount(id: Int, label: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCall { api.updateCalendarAccount(id, UpdateCalendarAccountRequest(label = label)) }) {
            is ApiResult.Success -> refreshCalendars()
            is ApiResult.Failure -> r
        }
    }

    /** Revokes the grant at Google and drops the account with its calendars and events. */
    suspend fun deleteAccount(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCallNoBody { api.deleteCalendarAccount(id) }) {
            is ApiResult.Success -> {
                dao.deleteEventsOfAccount(id)
                refreshCalendars()
            }

            is ApiResult.Failure -> if (r.code == 404) refreshCalendars() else r
        }
    }

    suspend fun resyncAccount(id: Int): ApiResult<SyncResult> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.resyncCalendarAccount(id) }
    }

    // --- calendar preferences ---------------------------------------------------------

    /**
     * Visibility is a server-side preference, not a local filter, so every device agrees on it.
     * The cached events of a hidden calendar are dropped because they are no longer fetched —
     * leaving them would show a calendar the user just switched off.
     */
    suspend fun setCalendarVisible(id: Int, visible: Boolean): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCall { api.updateCalendar(id, UpdateCalendarRequest(visible = visible)) }) {
            is ApiResult.Success -> {
                if (!visible) dao.deleteEventsOfCalendar(id)
                upsertOne(r.data)
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> r
        }
    }

    /**
     * Turning sync on imports the calendar in full: gv-api cannot bound the initial import by
     * date (Google forbids `timeMin` next to a sync token), which is why holiday and birthday
     * calendars arrive switched off. The sync is kicked off here so the events appear without
     * waiting for the next poll.
     *
     * Turning it off deletes the calendar's events server-side, so they go from the cache too.
     */
    suspend fun setCalendarSync(id: Int, enabled: Boolean): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCall { api.updateCalendar(id, UpdateCalendarRequest(sync_enabled = enabled)) }) {
            is ApiResult.Success -> {
                if (!enabled) dao.deleteEventsOfCalendar(id)
                upsertOne(r.data)
                if (enabled) safeApiCall { api.syncCalendar(id) }
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> r
        }
    }

    suspend fun syncNow(calendarId: Int? = null): ApiResult<SyncResult> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.syncCalendar(calendarId) }
    }

    // --- event writes ------------------------------------------------------------------

    suspend fun createEvent(request: CreateEventRequest): ApiResult<CalendarEvent> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createCalendarEvent(request) }
    }

    /** [ref] is an [CalendarEvent.instance_id], passed back verbatim. */
    suspend fun updateEvent(ref: String, request: UpdateEventRequest): ApiResult<CalendarEvent> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateCalendarEvent(ref, request) }
    }

    suspend fun deleteEvent(ref: String, scope: String?, sendUpdates: String?): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCallNoBody { api.deleteCalendarEvent(ref, scope, sendUpdates) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            // Already gone in Google is the outcome the caller wanted.
            is ApiResult.Failure -> if (r.code == 404) ApiResult.Success(Unit) else r
        }
    }

    suspend fun moveEvent(ref: String, calendarId: Int, sendUpdates: String?): ApiResult<MoveEventResult> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.moveCalendarEvent(ref, MoveEventRequest(calendarId, sendUpdates)) }
    }

    // --- live updates -----------------------------------------------------------------

    /** gv-api's change stream. See [CalendarStream] — collect it to subscribe, stop to hang up. */
    fun liveUpdates(): Flow<CalendarStreamEvent> = stream.events()

    // --- CacheRefresher ---------------------------------------------------------------

    /**
     * Background warm-up. Keeps the month around today readable offline, which is the range
     * anyone opening the app is going to look at.
     */
    override suspend fun refresh() {
        runCatching {
            refreshCalendars()
            val now = Instant.now()
            refreshRange(now.minus(Duration.ofDays(14)), now.plus(Duration.ofDays(45)))
        }
    }

    private suspend fun upsertOne(calendar: GoogleCalendar) {
        dao.upsertCalendars(listOf(calendar.toEntity()))
    }

    private companion object {
        /**
         * How far past the requested range to fetch and cache. One day covers the worst zone
         * disagreement between a calendar's own zone and the phone's for an all-day event.
         */
        val PAD: Duration = Duration.ofDays(1)

        val RFC3339: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

        fun padStart(from: Instant): Instant = from.minus(PAD)

        fun padEnd(to: Instant): Instant = to.plus(PAD)
    }
}

// --- mapping ---------------------------------------------------------------------------

private val mappingGson = Gson()
private val stringListType = object : TypeToken<List<String>>() {}.type
private val attendeeListType = object : TypeToken<List<EventAttendee>>() {}.type

private fun GoogleCalendar.toEntity() = CalendarEntity(
    id = id,
    accountId = account_id,
    accountEmail = account_email,
    accountStatus = account_status,
    summary = summary,
    timeZone = time_zone,
    color = color,
    accessRole = access_role,
    writable = writable,
    isPrimary = is_primary,
    syncEnabled = sync_enabled,
    visible = visible,
    deleted = deleted,
    lastSyncAt = sync.last_sync_at,
    lastSyncError = sync.last_sync_error,
    watchActive = sync.watch_active,
)

private fun CalendarEntity.toDomain() = GoogleCalendar(
    id = id,
    account_id = accountId,
    account_email = accountEmail,
    account_status = accountStatus,
    google_calendar_id = "",
    summary = summary,
    time_zone = timeZone,
    color = color,
    access_role = accessRole,
    writable = writable,
    is_primary = isPrimary,
    sync_enabled = syncEnabled,
    visible = visible,
    deleted = deleted,
    sync = CalendarSyncState(
        last_sync_at = lastSyncAt,
        last_sync_error = lastSyncError,
        watch_active = watchActive,
    ),
)

/**
 * Null for an event whose start cannot be read, which is the one field the cache cannot work
 * around: without an instant there is no range to store it in. Dropping the row is better than
 * storing it at the epoch, where it would haunt January 1970.
 */
private fun CalendarEvent.toEntityOrNull(): CalendarEventEntity? {
    val startMs = parseInstantOrNull(starts_at)?.toEpochMilli() ?: return null
    val endMs = parseInstantOrNull(ends_at)?.toEpochMilli() ?: startMs
    return CalendarEventEntity(
        instanceId = instance_id,
        eventId = event_id,
        calendarId = calendar_id,
        accountId = account_id,
        accountEmail = account_email,
        calendarName = calendar_name,
        color = color,
        googleEventId = google_event_id,
        summary = summary,
        description = description,
        location = location,
        status = status,
        eventType = event_type,
        allDay = all_day,
        startsAt = starts_at,
        endsAt = ends_at,
        startsAtMs = startMs,
        // A point in time overlaps no range, so give it a millisecond of width. See the entity.
        endsAtMs = maxOf(endMs, startMs + 1),
        timeZone = time_zone,
        startDate = start_date,
        endDate = end_date,
        recurring = recurring,
        recurrenceJson = recurrence?.takeIf { it.isNotEmpty() }?.let { mappingGson.toJson(it) },
        isException = is_exception,
        originalStartsAt = original_starts_at,
        editable = editable,
        organizerEmail = organizer_email,
        attendeesJson = attendees?.takeIf { it.isNotEmpty() }?.let { mappingGson.toJson(it) },
        htmlLink = html_link,
        createdByGv = created_by_gv,
    )
}

private fun CalendarEventEntity.toDomain() = CalendarEvent(
    instance_id = instanceId,
    event_id = eventId,
    calendar_id = calendarId,
    account_id = accountId,
    account_email = accountEmail,
    calendar_name = calendarName,
    color = color,
    google_event_id = googleEventId,
    summary = summary,
    description = description,
    location = location,
    status = status,
    event_type = eventType,
    all_day = allDay,
    starts_at = startsAt,
    ends_at = endsAt,
    time_zone = timeZone,
    start_date = startDate,
    end_date = endDate,
    recurring = recurring,
    recurrence = recurrenceJson?.let {
        runCatching { mappingGson.fromJson<List<String>>(it, stringListType) }.getOrNull()
    },
    is_exception = isException,
    original_starts_at = originalStartsAt,
    editable = editable,
    organizer_email = organizerEmail,
    attendees = attendeesJson?.let {
        runCatching { mappingGson.fromJson<List<EventAttendee>>(it, attendeeListType) }.getOrNull()
    },
    html_link = htmlLink,
    created_by_gv = createdByGv,
)
