package com.gv.app.data.repository

import androidx.room.withTransaction
import com.gv.app.data.api.ApiService
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.HabitDao
import com.gv.app.data.local.db.HabitDayEntity
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.domain.model.LogHabitRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Habits store. Online-first: every write goes straight to the server and the cached day is
 * re-read from the response, so what the screen shows is what the server has. Room holds the
 * last seen state per day purely so the list still renders (read-only) with no connection.
 *
 * Writes are refused offline by [OnlineGate] rather than queued — see that class for why.
 */
class HabitRepository(
    private val api: ApiService,
    private val db: GvDatabase,
    private val dao: HabitDao,
    private val gate: OnlineGate,
) : CacheRefresher {

    fun habitsForDate(date: LocalDate): Flow<List<HabitWithLog>> =
        dao.habitsForDate(date.toString()).map { rows -> rows.map { it.toDomain() } }

    /** Re-fetch a day and replace that day's cached rows. Failure leaves the cache untouched. */
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

    /**
     * Relative change (+/− buttons).
     *
     * The new absolute value is computed from the cached row because the API's log endpoint
     * takes an absolute value, not a delta. Rapid taps are therefore the caller's problem to
     * debounce — the ViewModel already does, and sending the absolute value means a lost
     * request cannot silently skip a step the way a lost delta would.
     */
    suspend fun adjustHabit(habitId: Int, date: LocalDate, delta: Double): ApiResult<Unit> {
        val current = dao.find(habitId, date.toString())
        val newValue = (current?.logValue ?: 0.0) + delta
        return setHabit(habitId, date, newValue)
    }

    /** Absolute set (numeric input). */
    suspend fun setHabit(habitId: Int, date: LocalDate, value: Double): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        val key = date.toString()
        return when (val r = safeApiCall { api.logHabit(LogHabitRequest(habit_id = habitId, date = key, value = value)) }) {
            // Re-read so server-computed period totals and streaks are right, not guessed.
            is ApiResult.Success -> refreshDate(date)
            is ApiResult.Failure -> r
        }
    }

    suspend fun deleteHabit(habitId: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCallNoBody { api.deleteHabit(habitId) }) {
            is ApiResult.Success -> {
                dao.deleteHabit(habitId)
                ApiResult.Success(Unit)
            }
            // Already gone server-side is the outcome the caller wanted.
            is ApiResult.Failure -> if (r.code == 404) {
                dao.deleteHabit(habitId)
                ApiResult.Success(Unit)
            } else {
                r
            }
        }
    }

    // --- CacheRefresher ---

    override suspend fun refresh() {
        runCatching { refreshDate(LocalDate.now()) }
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
