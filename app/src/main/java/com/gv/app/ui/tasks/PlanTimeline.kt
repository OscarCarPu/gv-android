package com.gv.app.ui.tasks

import com.gv.app.data.api.parseInstantOrNull
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.TimeEntryWithTaskResponse
import java.time.Instant
import java.time.ZoneId

/**
 * Today's plan folded over today's real time entries. Mirrors gv-web's `planOverlay.ts`.
 *
 * One agenda around a single "now" marker. Everything before it is rebuilt from what actually
 * happened: each time entry becomes an [PlanItem.Actual] at its real clock position, tied to
 * the block that planned it when one matches. Blocks that came and went unworked collapse to
 * [PlanItem.Skipped]; planned breaks nothing ran through become [PlanItem.Rest]; whatever is
 * left over is a [PlanItem.Gap]. Everything after now stays plain intent ([PlanItem.Planned]).
 *
 * All instants are epoch milliseconds; durations are whole seconds.
 */
sealed interface PlanItem {
    /** Work that happened, clamped to the past. Replaces the block that planned it. */
    data class Actual(
        val entryId: Int,
        val taskId: Int,
        val taskName: String,
        val projectName: String?,
        val comment: String?,
        val startedMs: Long,
        val endedMs: Long,
        val seconds: Long,
        val running: Boolean,
        /** The block this fulfils — same task, overlapping time — or null when there was none. */
        val block: PlanBlockResponse?,
        /** Planned length of [block], for the "27m of 1h" shortfall read. */
        val plannedSeconds: Long,
        /**
         * The task is planned today but this work fell outside its slot: [block] is null while
         * this names the slot it belongs to. Both null means the plan never mentioned the task.
         */
        val offScheduleBlock: PlanBlockResponse?,
    ) : PlanItem

    /** A past free-time block nothing was logged against: the break really happened. */
    data class Rest(val block: PlanBlockResponse, val seconds: Long) : PlanItem

    /** A past block that did not happen: a task block never worked, or a break worked through. */
    data class Skipped(
        val block: PlanBlockResponse,
        val seconds: Long,
        /** A free block that was overridden by real work. */
        val workedThrough: Boolean,
        /** The task was worked today, just not in this slot: "done at another time". */
        val movedElsewhere: Boolean,
    ) : PlanItem

    /** Past time nothing accounts for. */
    data class Gap(val fromMs: Long, val toMs: Long, val seconds: Long) : PlanItem

    data class Now(val ms: Long) : PlanItem

    /** Still-to-come intent, straight from the plan. */
    data class Planned(
        val block: PlanBlockResponse,
        /** The whole block, or only the part after now when it is in progress. */
        val remainingSeconds: Long,
        val current: Boolean,
    ) : PlanItem
}

data class PlanTimelineTotals(
    /** Everything logged today, planned or not. */
    val doneSeconds: Long,
    /** Planned work done outside its slot — a subset of [doneSeconds]. */
    val offScheduleSeconds: Long,
    /** Planned task time still ahead of now (the current block counts from now). */
    val remainingPlannedSeconds: Long,
    /** Planned task time that came and went with nothing logged. */
    val skippedSeconds: Long,
    /** Breaks taken as planned. */
    val restSeconds: Long,
    /** Past time no row accounts for. */
    val gapSeconds: Long,
)

data class PlanTimeline(val items: List<PlanItem>, val totals: PlanTimelineTotals)

/** Holes shorter than this are rounding noise, not gaps. */
private const val GAP_THRESHOLD_SECONDS = 120L

private data class Interval(val start: Long, val end: Long)

private fun overlap(a: Interval, b: Interval): Long =
    maxOf(0L, minOf(a.end, b.end) - maxOf(a.start, b.start))

private fun ms(iso: String): Long = parseInstantOrNull(iso)?.toEpochMilli() ?: 0L

fun blockSeconds(b: PlanBlockResponse): Long = maxOf(0L, (ms(b.ended_at) - ms(b.started_at)) / 1000)

private fun startOfDay(atMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

fun buildPlanTimeline(
    blocks: List<PlanBlockResponse>,
    entries: List<TimeEntryWithTaskResponse>,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): PlanTimeline {
    val dayStart = startOfDay(nowMs, zone)

    // 1. Actuals: clamp every entry into [dayStart, now] and tie it to a block.
    val actuals = mutableListOf<PlanItem.Actual>()
    for (e in entries) {
        val running = e.finished_at == null
        val rawStart = ms(e.started_at)
        val rawEnd = if (running) nowMs else ms(e.finished_at!!)
        val start = maxOf(rawStart, dayStart)
        // `now` only re-ticks once a minute, so a timer started seconds ago can sit ahead of
        // it. Clamping the end up to `start` keeps it visible at 0s rather than inverting.
        val end = maxOf(minOf(rawEnd, nowMs), start)
        val seconds = (end - start) / 1000
        // A finished entry with nothing in the past is noise or future-dated; a running one is
        // live by definition and exempt.
        if (seconds <= 0 && !running) continue

        // Best match is the same-task block sharing the most time with this entry. Failing
        // that, the earliest same-task block anywhere today: the work happened off-schedule.
        // The probe is at least 1ms wide so a timer started this second still matches the
        // block it sits inside.
        val probe = Interval(start, maxOf(end, start + 1))
        var block: PlanBlockResponse? = null
        var offSchedule: PlanBlockResponse? = null
        var best = 0L
        for (b in blocks) {
            if (b.task_id != e.task_id) continue
            val shared = overlap(probe, Interval(ms(b.started_at), ms(b.ended_at)))
            if (shared > best) {
                best = shared
                block = b
            }
            if (offSchedule == null || ms(b.started_at) < ms(offSchedule.started_at)) offSchedule = b
        }

        actuals.add(
            PlanItem.Actual(
                entryId = e.id,
                taskId = e.task_id,
                taskName = e.task_name,
                projectName = e.project_name,
                comment = e.comment,
                startedMs = start,
                endedMs = end,
                seconds = seconds,
                running = running,
                block = block,
                plannedSeconds = block?.let(::blockSeconds) ?: 0L,
                offScheduleBlock = if (block == null) offSchedule else null,
            ),
        )
    }
    actuals.sortBy { it.startedMs }

    val fulfilledBlockIds = actuals.mapNotNull { it.block?.id }.toSet()
    val workedTaskIds = actuals.map { it.taskId }.toSet()
    val occupied = actuals.map { Interval(it.startedMs, it.endedMs) }

    // 2. Past blocks that produced no actual row of their own.
    val rests = mutableListOf<PlanItem.Rest>()
    val skipped = mutableListOf<PlanItem.Skipped>()
    val future = mutableListOf<PlanItem.Planned>()

    for (b in blocks) {
        val start = ms(b.started_at)
        val end = ms(b.ended_at)

        if (end > nowMs) {
            future.add(
                PlanItem.Planned(
                    block = b,
                    remainingSeconds = maxOf(0L, (end - maxOf(start, nowMs)) / 1000),
                    current = start <= nowMs && nowMs < end,
                ),
            )
            // A block straddling now may have logged time already; its actual row covers that.
            continue
        }

        if (b.task_id == null) {
            // A free block is honoured when no real work ran through it.
            val worked = occupied.any { overlap(it, Interval(start, end)) > 0 }
            if (worked) {
                skipped.add(PlanItem.Skipped(b, blockSeconds(b), workedThrough = true, movedElsewhere = false))
            } else {
                rests.add(PlanItem.Rest(b, blockSeconds(b)))
            }
            continue
        }

        // A task block with nothing logged in its window: either the task was worked at some
        // other hour (moved) or it simply did not happen.
        if (b.id !in fulfilledBlockIds) {
            skipped.add(
                PlanItem.Skipped(
                    block = b,
                    seconds = blockSeconds(b),
                    workedThrough = false,
                    movedElsewhere = b.task_id in workedTaskIds,
                ),
            )
        }
    }

    // 3. Gaps: past time no other row accounts for. Skipped blocks count as accounted-for —
    // their row already explains the hole, so a gap over the same minutes would say it twice.
    val consuming = (
        occupied +
            rests.map { Interval(ms(it.block.started_at), ms(it.block.ended_at)) } +
            skipped.map { Interval(ms(it.block.started_at), ms(it.block.ended_at)) }
        ).sortedBy { it.start }

    // Merge overlaps so two parallel-ish entries cannot fabricate a negative gap.
    val merged = mutableListOf<Interval>()
    for (c in consuming) {
        val last = merged.lastOrNull()
        if (last != null && c.start <= last.end) {
            merged[merged.lastIndex] = last.copy(end = maxOf(last.end, c.end))
        } else {
            merged.add(c)
        }
    }

    val gaps = mutableListOf<PlanItem.Gap>()
    if (merged.isNotEmpty()) {
        for (i in 1 until merged.size) {
            val seconds = (merged[i].start - merged[i - 1].end) / 1000
            if (seconds > GAP_THRESHOLD_SECONDS) {
                gaps.add(PlanItem.Gap(merged[i - 1].end, merged[i].start, seconds))
            }
        }
        val tail = (nowMs - merged.last().end) / 1000
        if (tail > GAP_THRESHOLD_SECONDS) gaps.add(PlanItem.Gap(merged.last().end, nowMs, tail))
    }

    // 4. Assemble: the past as it happened, the now marker, then what is still ahead.
    fun sortKey(item: PlanItem): Long = when (item) {
        is PlanItem.Actual -> item.startedMs
        is PlanItem.Rest -> ms(item.block.started_at)
        is PlanItem.Skipped -> ms(item.block.started_at)
        is PlanItem.Gap -> item.fromMs
        else -> 0L
    }

    val past: List<PlanItem> = (actuals + rests + skipped + gaps).sortedBy(::sortKey)
    future.sortBy { ms(it.block.started_at) }
    val items = past + PlanItem.Now(nowMs) + future

    // 5. Totals.
    return PlanTimeline(
        items = items,
        totals = PlanTimelineTotals(
            doneSeconds = actuals.sumOf { it.seconds },
            offScheduleSeconds = actuals.filter { it.offScheduleBlock != null }.sumOf { it.seconds },
            remainingPlannedSeconds = future.filter { it.block.task_id != null }.sumOf { it.remainingSeconds },
            // A break worked through is not a shortfall, and neither is a task moved to another
            // hour: only genuinely untouched task time counts as skipped.
            skippedSeconds = skipped.filter { !it.workedThrough && !it.movedElsewhere }.sumOf { it.seconds },
            restSeconds = rests.sumOf { it.seconds },
            gapSeconds = gaps.sumOf { it.seconds },
        ),
    )
}

/**
 * The plan's headline: what is done, and where the day lands if every remaining block is
 * honoured. Derived from the [PlanTimeline] rather than from the API's day summary, so the bar
 * and the rows beneath it can never disagree.
 */
data class PlanSummary(
    val doneSeconds: Long,
    /** Done so far plus the planned task time still ahead of now. */
    val estimatedSeconds: Long,
    val dailyTargetSeconds: Long,
    val freeSeconds: Long,
    val skippedSeconds: Long,
) {
    /** Share of the bar already earned, so the fill can tell done apart from projected. */
    val donePct: Float get() = pct(doneSeconds)
    val estimatedPct: Float get() = pct(estimatedSeconds)
    val estimatedReached: Boolean get() = dailyTargetSeconds > 0 && estimatedSeconds >= dailyTargetSeconds

    private fun pct(seconds: Long): Float =
        if (dailyTargetSeconds > 0) (seconds.toFloat() / dailyTargetSeconds).coerceIn(0f, 1f) else 0f
}

fun buildPlanSummary(timeline: PlanTimeline, dailyTargetSeconds: Long, freeSeconds: Long): PlanSummary =
    PlanSummary(
        doneSeconds = timeline.totals.doneSeconds,
        estimatedSeconds = timeline.totals.doneSeconds + timeline.totals.remainingPlannedSeconds,
        dailyTargetSeconds = dailyTargetSeconds,
        freeSeconds = freeSeconds,
        skippedSeconds = timeline.totals.skippedSeconds,
    )
