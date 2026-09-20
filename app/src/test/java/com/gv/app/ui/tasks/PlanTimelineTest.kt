package com.gv.app.ui.tasks

import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.TimeEntryWithTaskResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * Today's plan folded over what actually happened. The cases are the ones that read plausibly
 * while being wrong: a task worked at the wrong hour reported as "not done", a break counted
 * twice, a timer started this second missing from the list.
 */
class PlanTimelineTest {

    private val zone = ZoneOffset.UTC
    private val day = "2026-08-29"

    /** `HH:MM` on [day], as the API writes an instant. */
    private fun at(hhmm: String) = "${day}T$hhmm:00Z"
    private fun ms(hhmm: String) = Instant.parse(at(hhmm)).toEpochMilli()

    private val now = ms("12:00")

    private fun block(id: Int, from: String, to: String, taskId: Int? = null) = PlanBlockResponse(
        id = id, plan_date = "${day}T00:00:00Z", started_at = at(from), ended_at = at(to),
        task_id = taskId, task_name = taskId?.let { "Task $it" }, label = if (taskId != null) "Task $taskId" else "Break",
        note = null, task_type = null, task_recurrence = null, task_started_at = null, task_finished_at = null,
    )

    private fun entry(id: Int, taskId: Int, from: String, to: String?) = TimeEntryWithTaskResponse(
        id = id, task_id = taskId, task_name = "Task $taskId", task_type = "standard", recurrence = null,
        priority = 3, project_id = null, project_name = null, started_at = at(from),
        finished_at = to?.let(::at), comment = null, task_finished_at = null, time_spent = 0,
    )

    private fun timeline(blocks: List<PlanBlockResponse>, entries: List<TimeEntryWithTaskResponse>, nowMs: Long = now) =
        buildPlanTimeline(blocks, entries, nowMs, zone)

    private inline fun <reified T : PlanItem> PlanTimeline.all(): List<T> = items.filterIsInstance<T>()

    // --- actuals --------------------------------------------------------------------------

    @Test
    fun `work inside its planned slot is attributed to that block`() {
        val b = block(1, "09:00", "10:00", taskId = 7)
        val actual = timeline(listOf(b), listOf(entry(1, 7, "09:00", "09:27"))).all<PlanItem.Actual>().single()
        assertEquals(b, actual.block)
        assertEquals(27 * 60L, actual.seconds)
        assertEquals(60 * 60L, actual.plannedSeconds)
        assertNull(actual.offScheduleBlock)
    }

    @Test
    fun `work outside its slot is off-schedule, not unplanned`() {
        val b = block(1, "15:00", "16:00", taskId = 7)
        val actual = timeline(listOf(b), listOf(entry(1, 7, "09:00", "10:00"))).all<PlanItem.Actual>().single()
        assertNull(actual.block)
        assertEquals(b, actual.offScheduleBlock)
    }

    @Test
    fun `work on a task the plan never mentions has neither`() {
        val actual = timeline(emptyList(), listOf(entry(1, 7, "09:00", "10:00"))).all<PlanItem.Actual>().single()
        assertNull(actual.block)
        assertNull(actual.offScheduleBlock)
    }

    @Test
    fun `an entry from yesterday is clamped to the start of today`() {
        val e = entry(1, 7, "09:00", "10:00").copy(started_at = "2026-08-28T23:00:00Z", finished_at = at("01:00"))
        val actual = timeline(emptyList(), listOf(e)).all<PlanItem.Actual>().single()
        assertEquals(ms("00:00"), actual.startedMs)
        assertEquals(60 * 60L, actual.seconds)
    }

    @Test
    fun `a running entry counts up to now`() {
        val actual = timeline(emptyList(), listOf(entry(1, 7, "11:00", null))).all<PlanItem.Actual>().single()
        assertTrue(actual.running)
        assertEquals(now, actual.endedMs)
        assertEquals(60 * 60L, actual.seconds)
    }

    @Test
    fun `a timer started a moment after the last tick stays visible at zero`() {
        val ahead = entry(1, 7, "12:00", null).copy(started_at = "${day}T12:00:30Z")
        val actual = timeline(emptyList(), listOf(ahead)).all<PlanItem.Actual>().single()
        assertEquals(0L, actual.seconds)
    }

    @Test
    fun `a finished entry with nothing in the past is dropped`() {
        assertEquals(emptyList<PlanItem.Actual>(), timeline(emptyList(), listOf(entry(1, 7, "13:00", "14:00"))).all<PlanItem.Actual>())
    }

    // --- past blocks ----------------------------------------------------------------------

    @Test
    fun `a task block nothing was logged against is skipped`() {
        val skipped = timeline(listOf(block(1, "09:00", "10:00", taskId = 7)), emptyList()).all<PlanItem.Skipped>().single()
        assertEquals(false, skipped.movedElsewhere)
        assertEquals(false, skipped.workedThrough)
    }

    @Test
    fun `a task worked at another hour is moved, not skipped`() {
        val t = timeline(listOf(block(1, "09:00", "10:00", taskId = 7)), listOf(entry(1, 7, "10:30", "11:30")))
        assertTrue(t.all<PlanItem.Skipped>().single().movedElsewhere)
        assertEquals(0L, t.totals.skippedSeconds)
    }

    @Test
    fun `an unworked planned break is rest`() {
        val t = timeline(listOf(block(1, "10:00", "10:30")), emptyList())
        assertEquals(30 * 60L, t.all<PlanItem.Rest>().single().seconds)
        assertEquals(30 * 60L, t.totals.restSeconds)
    }

    @Test
    fun `a break worked through is skipped, but is not a shortfall`() {
        val t = timeline(listOf(block(1, "10:00", "11:00")), listOf(entry(1, 7, "10:00", "11:00")))
        val skipped = t.all<PlanItem.Skipped>().single()
        assertTrue(skipped.workedThrough)
        assertEquals(0L, t.totals.skippedSeconds)
    }

    // --- future ---------------------------------------------------------------------------

    @Test
    fun `a future block stays plan and counts in full`() {
        val t = timeline(listOf(block(1, "14:00", "15:00", taskId = 7)), emptyList())
        val planned = t.all<PlanItem.Planned>().single()
        assertEquals(60 * 60L, planned.remainingSeconds)
        assertEquals(false, planned.current)
        assertEquals(60 * 60L, t.totals.remainingPlannedSeconds)
    }

    @Test
    fun `the block containing now is current and counts only what is left`() {
        val planned = timeline(listOf(block(1, "11:30", "12:30", taskId = 7)), emptyList()).all<PlanItem.Planned>().single()
        assertTrue(planned.current)
        assertEquals(30 * 60L, planned.remainingSeconds)
    }

    @Test
    fun `a planned break ahead does not count as remaining task time`() {
        assertEquals(0L, timeline(listOf(block(1, "14:00", "15:00")), emptyList()).totals.remainingPlannedSeconds)
    }

    // --- gaps -----------------------------------------------------------------------------

    @Test
    fun `unexplained time between entries is a gap`() {
        val t = timeline(emptyList(), listOf(entry(1, 7, "09:00", "10:00"), entry(2, 7, "10:30", "11:00")))
        val gap = t.all<PlanItem.Gap>().first()
        assertEquals(ms("10:00"), gap.fromMs)
        assertEquals(ms("10:30"), gap.toMs)
    }

    @Test
    fun `time since the last entry up to now is a trailing gap`() {
        val gap = timeline(emptyList(), listOf(entry(1, 7, "09:00", "10:00"))).all<PlanItem.Gap>().single()
        assertEquals(ms("10:00"), gap.fromMs)
        assertEquals(now, gap.toMs)
    }

    @Test
    fun `holes under two minutes are rounding noise`() {
        val e1 = entry(1, 7, "09:00", "10:00")
        val e2 = entry(2, 7, "10:00", "11:59").copy(started_at = "${day}T10:01:00Z")
        assertEquals(emptyList<PlanItem.Gap>(), timeline(emptyList(), listOf(e1, e2)).all<PlanItem.Gap>().filter { it.toMs < ms("11:00") })
    }

    @Test
    fun `a skipped block explains its hole, so no gap is reported over it`() {
        val t = timeline(
            listOf(block(1, "10:00", "11:00", taskId = 9)),
            listOf(entry(1, 7, "09:00", "10:00"), entry(2, 7, "11:00", "12:00")),
        )
        assertEquals(emptyList<PlanItem.Gap>(), t.all<PlanItem.Gap>())
    }

    // --- shape ----------------------------------------------------------------------------

    @Test
    fun `the past comes first, then now, then what is ahead`() {
        val t = timeline(
            listOf(block(1, "14:00", "15:00", taskId = 8), block(2, "09:00", "10:00", taskId = 7)),
            listOf(entry(1, 7, "09:00", "10:00")),
        )
        val kinds = t.items.map { it::class.simpleName }
        assertEquals(listOf("Actual", "Gap", "Now", "Planned"), kinds)
        assertEquals(now, (t.items[2] as PlanItem.Now).ms)
    }

    @Test
    fun `totals add up done, off-schedule and skipped separately`() {
        val t = timeline(
            listOf(block(1, "08:00", "09:00", taskId = 5), block(2, "15:00", "16:00", taskId = 7)),
            listOf(entry(1, 7, "10:00", "11:00")),
        )
        assertEquals(60 * 60L, t.totals.doneSeconds)
        assertEquals(60 * 60L, t.totals.offScheduleSeconds)
        assertEquals(60 * 60L, t.totals.skippedSeconds)
        assertEquals(60 * 60L, t.totals.remainingPlannedSeconds)
    }

    // --- summary --------------------------------------------------------------------------

    @Test
    fun `the estimate is what is done plus what the plan still holds`() {
        val t = timeline(listOf(block(1, "14:00", "16:00", taskId = 8)), listOf(entry(1, 7, "09:00", "10:00")))
        val summary = buildPlanSummary(t, dailyTargetSeconds = 6 * 3600L, freeSeconds = 2 * 3600L)
        assertEquals(3 * 3600L, summary.estimatedSeconds)
        assertEquals(0.5f, summary.estimatedPct, 0.001f)
        assertEquals(1f / 6f, summary.donePct, 0.001f)
        assertEquals(false, summary.estimatedReached)
    }

    @Test
    fun `reaching the target is reported and the bar stops at full`() {
        val t = timeline(listOf(block(1, "14:00", "20:00", taskId = 8)), listOf(entry(1, 7, "09:00", "11:00")))
        val summary = buildPlanSummary(t, dailyTargetSeconds = 4 * 3600L, freeSeconds = 0)
        assertTrue(summary.estimatedReached)
        assertEquals(1f, summary.estimatedPct, 0f)
    }

    @Test
    fun `no target means an empty bar, not a divide by zero`() {
        val summary = buildPlanSummary(timeline(emptyList(), emptyList()), dailyTargetSeconds = 0, freeSeconds = 0)
        assertEquals(0f, summary.estimatedPct, 0f)
        assertEquals(false, summary.estimatedReached)
    }
}
