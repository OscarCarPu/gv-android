package com.gv.app.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.gv.app.data.api.ApiService
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.HabitDao
import com.gv.app.data.local.db.HabitDayEntity
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.data.sync.Outbox
import com.gv.app.data.sync.OutboxHandler
import com.gv.app.data.sync.SyncOutcome
import com.gv.app.data.sync.SyncScheduler
import com.gv.app.data.sync.toSyncOutcome
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Offline-first habits store. Reads are served from Room (instant, works offline) while a
 * background fetch reconciles; the date-paged UI therefore renders any day from cache. Writes
 * commit locally and enqueue an outbox row in the same transaction, so a log survives even if
 * the user immediately flips the day or loses connectivity. Also the [OutboxHandler] that
 * replays habit logs / deletes, and a [CacheRefresher] for the periodic warm-up.
 */
class HabitRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: HabitDao,
    private val outbox: Outbox,
    private val sync: SyncScheduler,
    private val gson: Gson = Gson(),
) : OutboxHandler, CacheRefresher {

    fun habitsForDate(date: LocalDate): Flow<List<HabitWithLog>> =
        dao.habitsForDate(date.toString()).map { rows -> rows.map { it.toDomain() } }

    /** Re-fetch a day from the server and replace that day's cached rows. */
    suspend fun refreshDate(date: LocalDate): ApiResult<Unit> {
        val key = date.toString()
        return when (val result = safeApiCall { api.getHabits(key) }) {
            is ApiResult.Success -> {
                val rows = result.data.map { it.toEntity(key) }
                db.withTransaction {
                    dao.upsertAll(rows)
                    dao.deleteMissingForDate(key, rows.map { it.id })
                }
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    /** Relative change (+/- buttons). Read-modify-write is atomic so rapid taps don't race. */
    suspend fun adjustHabit(habitId: Int, date: LocalDate, delta: Double) {
        val key = date.toString()
        val changed = db.withTransaction {
            val current = dao.find(habitId, key) ?: return@withTransaction false
            val newValue = (current.logValue ?: 0.0) + delta
            dao.upsert(current.copy(logValue = newValue, periodValue = current.periodValue + delta))
            enqueueLog(habitId, key, newValue)
            true
        }
        if (changed) sync.requestFlush()
    }

    /** Absolute set (numeric input). */
    suspend fun setHabit(habitId: Int, date: LocalDate, value: Double) {
        val key = date.toString()
        db.withTransaction {
            dao.find(habitId, key)?.let { current ->
                val delta = value - (current.logValue ?: 0.0)
                dao.upsert(current.copy(logValue = value, periodValue = current.periodValue + delta))
            }
            enqueueLog(habitId, key, value)
        }
        sync.requestFlush()
    }

    suspend fun deleteHabit(habitId: Int) {
        db.withTransaction {
            dao.deleteHabit(habitId)
            outbox.enqueueDelete(ENTITY_HABIT, habitId.toString())
        }
        sync.requestFlush()
    }

    private suspend fun enqueueLog(habitId: Int, dateKey: String, value: Double) {
        val payload = gson.toJson(LogHabitRequest(habit_id = habitId, date = dateKey, value = value))
        outbox.enqueueUpsert(ENTITY_HABIT_LOG, "$habitId:$dateKey", payload)
    }

    // --- OutboxHandler ---

    override val entityTypes: Set<String> = setOf(ENTITY_HABIT_LOG, ENTITY_HABIT)

    override suspend fun sync(api: ApiService, mutation: OutboxMutation): SyncOutcome =
        when (mutation.entityType) {
            ENTITY_HABIT_LOG -> {
                val req = gson.fromJson(mutation.payloadJson, LogHabitRequest::class.java)
                when (val r = safeApiCall { api.logHabit(req) }) {
                    is ApiResult.Success -> {
                        // Reconcile server-computed period/streak for the affected day.
                        runCatching { refreshDate(LocalDate.parse(req.date)) }
                        SyncOutcome.Synced
                    }
                    is ApiResult.Failure -> r.toSyncOutcome()
                }
            }
            ENTITY_HABIT -> {
                val id = mutation.entityId.toIntOrNull()
                    ?: return SyncOutcome.DeadLetter("Bad habit id ${mutation.entityId}")
                when (val r = safeApiCallNoBody { api.deleteHabit(id) }) {
                    is ApiResult.Success -> SyncOutcome.Synced
                    is ApiResult.Failure -> if (r.code == 404) SyncOutcome.Synced else r.toSyncOutcome()
                }
            }
            else -> SyncOutcome.DeadLetter("Unhandled type ${mutation.entityType}")
        }

    // --- CacheRefresher ---

    override suspend fun refresh() {
        runCatching { refreshDate(LocalDate.now()) }
    }

    companion object {
        const val ENTITY_HABIT_LOG = "habit_log"
        const val ENTITY_HABIT = "habit"
    }
}

private fun HabitDayEntity.toDomain() = HabitWithLog(
    id = id,
    name = name,
    description = description,
    frequency = frequency,
    target_min = targetMin,
    target_max = targetMax,
    recording_required = recordingRequired,
    log_value = logValue,
    period_value = periodValue,
    current_streak = currentStreak,
    longest_streak = longestStreak,
)

private fun HabitWithLog.toEntity(date: String) = HabitDayEntity(
    id = id,
    date = date,
    name = name,
    description = description,
    frequency = frequency,
    targetMin = target_min,
    targetMax = target_max,
    recordingRequired = recording_required,
    logValue = log_value,
    periodValue = period_value,
    currentStreak = current_streak,
    longestStreak = longest_streak,
)
