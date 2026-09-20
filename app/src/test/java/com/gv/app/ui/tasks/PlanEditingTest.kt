package com.gv.app.ui.tasks

import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.domain.model.TaskFastResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The small rules behind the plan editor, the free-time strip and the task picker. */
class PlanEditingTest {

    private val day = LocalDate.parse("2026-08-29")

    // --- block times ----------------------------------------------------------------------

    @Test
    fun `a block stays on its day`() {
        val (from, to) = blockInstants(day, LocalTime.of(9, 0), LocalTime.of(10, 30))
        assertEquals(day.atTime(9, 0), from)
        assertEquals(day.atTime(10, 30), to)
    }

    @Test
    fun `an end of midnight means the end of that day, not its start`() {
        val (from, to) = blockInstants(day, LocalTime.of(22, 0), LocalTime.MIDNIGHT)
        assertEquals(day.plusDays(1).atStartOfDay(), to)
        assertTrue(to.isAfter(from))
    }

    @Test
    fun `an end before the start is left for the caller to reject`() {
        val (from, to) = blockInstants(day, LocalTime.of(10, 0), LocalTime.of(9, 0))
        assertTrue(!to.isAfter(from))
    }

    // --- free-time strip ------------------------------------------------------------------

    private fun free(capacity: String, free: String) = DayFreeBusy("2026-08-29", capacity, "0", free)

    @Test
    fun `free share is hours free over capacity`() {
        assertEquals(50f, freePercent(free("8", "4")), 0.001f)
    }

    @Test
    fun `no capacity or unreadable hours read as full, not as an error`() {
        assertEquals(0f, freePercent(free("0", "4")), 0f)
        assertEquals(0f, freePercent(free("abc", "4")), 0f)
        assertEquals(0f, freePercent(free("8", "")), 0f)
    }

    @Test
    fun `more free than capacity is capped`() {
        assertEquals(100f, freePercent(free("8", "12")), 0f)
    }

    // --- task picker ----------------------------------------------------------------------

    private fun task(id: Int, name: String, projectId: Int?, project: String?) =
        TaskFastResponse(id, name, projectId, project, "standard", null, 3)

    private val tasks = listOf(
        task(1, "Write report", 10, "Work"),
        task(2, "Fix bike", 20, "Home"),
        task(3, "Review report", 10, "Work"),
        task(4, "Loose end", null, null),
    )

    @Test
    fun `tasks group by project and keep their order`() {
        val groups = groupPickerTasks(tasks, "")
        assertEquals(listOf("Work", "Home", "No project"), groups.map { it.first })
        assertEquals(listOf(1, 3), groups.first().second.map { it.id })
    }

    @Test
    fun `search is a case-insensitive substring of the name`() {
        val groups = groupPickerTasks(tasks, "  REPORT ")
        assertEquals(listOf("Work"), groups.map { it.first })
        assertEquals(listOf(1, 3), groups.single().second.map { it.id })
    }

    @Test
    fun `a search that matches nothing yields no groups`() {
        assertEquals(emptyList<Pair<String, List<TaskFastResponse>>>(), groupPickerTasks(tasks, "zzz"))
    }
}
