package com.gv.app.ui.tasks

import com.gv.app.domain.model.TaskByDueDateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Due Soon's ordering. Every case here is a task that renders fine in the wrong place: an
 * urgent task buried under "later", a task due today demoted by its estimate.
 */
class DueSoonTest {

    private val today = LocalDate.parse("2026-08-29")

    private fun task(
        id: Int,
        dueAt: String? = null,
        projectDueAt: String? = null,
        priority: Int = 3,
        urgent: Boolean = false,
        startBy: String? = null,
        remainingHours: String? = null,
    ) = TaskByDueDateResponse(
        id = id, name = "Task $id", description = null, due_at = dueAt, started_at = null,
        task_type = "standard", recurrence = null, priority = priority, time_spent = 0,
        project_id = null, project_name = null, project_due_at = projectDueAt,
        depends_on = emptyList(), blocks = emptyList(), blocked = false,
        estimate_hours = null, remaining_hours = remainingHours, start_by = startBy, urgent = urgent,
    )

    private fun tiers(tasks: List<TaskByDueDateResponse>): Map<DueSoonTier, List<Int>> =
        groupTasksByUrgency(tasks, today).associate { g -> g.tier to g.tasks.map { it.id } }

    // --- tiers ----------------------------------------------------------------------------

    @Test
    fun `a past-due task is overdue whether or not it is urgent`() {
        assertEquals(listOf(1), tiers(listOf(task(1, "2026-08-20T00:00:00Z")))[DueSoonTier.OVERDUE])
    }

    @Test
    fun `an urgent task that is not yet due lands in today`() {
        val t = task(1, "2026-08-30T00:00:00Z", urgent = true, startBy = "2026-08-29")
        assertEquals(listOf(1), tiers(listOf(t))[DueSoonTier.TODAY])
    }

    @Test
    fun `a non-urgent task due within a week is this week`() {
        assertEquals(listOf(1), tiers(listOf(task(1, "2026-09-03T00:00:00Z")))[DueSoonTier.WEEK])
    }

    @Test
    fun `a non-urgent task due beyond a week is later`() {
        assertEquals(listOf(1), tiers(listOf(task(1, "2026-09-28T00:00:00Z")))[DueSoonTier.LATER])
    }

    @Test
    fun `a task with no estimate due tomorrow is never promoted to today`() {
        val result = tiers(listOf(task(1, "2026-08-30T00:00:00Z")))
        assertEquals(listOf(1), result[DueSoonTier.WEEK])
        assertEquals(emptyList<Int>(), result[DueSoonTier.TODAY])
    }

    @Test
    fun `a task due today stays in today even without an estimate`() {
        val result = tiers(listOf(task(1, "2026-08-29T00:00:00Z")))
        assertEquals(listOf(1), result[DueSoonTier.TODAY])
        assertEquals(emptyList<Int>(), result[DueSoonTier.WEEK])
    }

    @Test
    fun `a task with only a project due date is placed by it`() {
        assertEquals(listOf(1), tiers(listOf(task(1, projectDueAt = "2026-08-20T00:00:00Z")))[DueSoonTier.OVERDUE])
    }

    @Test
    fun `a task with no date at all still appears, last within later`() {
        val dated = task(1, "2026-09-28T00:00:00Z")
        val undated = task(2)
        assertEquals(listOf(1, 2), tiers(listOf(undated, dated))[DueSoonTier.LATER])
    }

    @Test
    fun `all four tiers are always present and in order`() {
        assertEquals(
            listOf(DueSoonTier.OVERDUE, DueSoonTier.TODAY, DueSoonTier.WEEK, DueSoonTier.LATER),
            groupTasksByUrgency(emptyList(), today).map { it.tier },
        )
    }

    // --- ordering within a tier -----------------------------------------------------------

    @Test
    fun `today sorts by real due date with priority breaking ties`() {
        val a = task(1, "2026-08-30T00:00:00Z", urgent = true, priority = 3)
        val b = task(2, "2026-08-30T00:00:00Z", urgent = true, priority = 1)
        val c = task(3, "2026-08-31T00:00:00Z", urgent = true, priority = 1)
        assertEquals(listOf(2, 1, 3), tiers(listOf(a, b, c))[DueSoonTier.TODAY])
    }

    @Test
    fun `week and later sort by start_by ahead of the due date`() {
        // Same due date, but task 1 has to be started sooner. Plain date order would miss that.
        val a = task(1, "2026-09-20T00:00:00Z", startBy = "2026-09-10")
        val b = task(2, "2026-09-20T00:00:00Z", startBy = "2026-09-15")
        assertEquals(listOf(1, 2), tiers(listOf(b, a))[DueSoonTier.LATER])
    }

    // --- truncation -----------------------------------------------------------------------

    @Test
    fun `truncation spends the budget in tier order and drops emptied tiers`() {
        val groups = listOf(
            DueSoonGroup(DueSoonTier.OVERDUE, listOf(task(1), task(2))),
            DueSoonGroup(DueSoonTier.TODAY, listOf(task(3))),
            DueSoonGroup(DueSoonTier.WEEK, emptyList()),
            DueSoonGroup(DueSoonTier.LATER, listOf(task(4), task(5), task(6))),
        )
        val result = truncateDueSoonGroups(groups, 4)
        assertEquals(
            mapOf(DueSoonTier.OVERDUE to listOf(1, 2), DueSoonTier.TODAY to listOf(3), DueSoonTier.LATER to listOf(4)),
            result.associate { it.tier to it.tasks.map { t -> t.id } },
        )
    }

    @Test
    fun `truncation keeps everything when the budget covers it`() {
        val groups = listOf(DueSoonGroup(DueSoonTier.TODAY, listOf(task(1), task(2))))
        assertEquals(2, truncateDueSoonGroups(groups, 10).single().tasks.size)
    }

    @Test
    fun `a zero budget yields nothing`() {
        assertEquals(emptyList<DueSoonGroup>(), truncateDueSoonGroups(listOf(DueSoonGroup(DueSoonTier.TODAY, listOf(task(1)))), 0))
    }

    // --- urgency phrase -------------------------------------------------------------------

    @Test
    fun `no phrase unless the task is urgent`() {
        assertNull(buildUrgencyPhrase(task(1, urgent = false, startBy = "2026-08-01", remainingHours = "6"), today))
    }

    @Test
    fun `no phrase without an estimate to talk about`() {
        assertNull(buildUrgencyPhrase(task(1, urgent = true, startBy = "2026-08-01", remainingHours = null), today))
    }

    @Test
    fun `a task that must start today says so`() {
        assertEquals("6h left · start today", buildUrgencyPhrase(task(1, urgent = true, startBy = "2026-08-29", remainingHours = "6"), today))
    }

    @Test
    fun `a task a day late says yesterday, later ones count days`() {
        assertEquals(
            "2.5h left · should've started yesterday",
            buildUrgencyPhrase(task(1, urgent = true, startBy = "2026-08-28", remainingHours = "2.5"), today),
        )
        assertEquals(
            "6h left · should've started 4d ago",
            buildUrgencyPhrase(task(1, urgent = true, startBy = "2026-08-25", remainingHours = "6"), today),
        )
    }
}
