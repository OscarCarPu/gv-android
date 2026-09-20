package com.gv.app.ui.tasks

import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.TaskByDueDateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskBoardTest {

    private val today = LocalDate.parse("2026-08-29")

    private fun task(id: Int, dueAt: String? = null, priority: Int = 3, projectId: Int? = null) = TaskByDueDateResponse(
        id = id, name = "Task $id", description = null, due_at = dueAt, started_at = null,
        task_type = "standard", recurrence = null, priority = priority, time_spent = 0,
        project_id = projectId, project_name = null, project_due_at = null,
        depends_on = emptyList(), blocks = emptyList(), blocked = false,
    )

    private fun project(id: Int, vararg children: ActiveTreeNode) = ActiveTreeNode(
        id = id, type = "project", name = "Project $id", description = null, due_at = null, started_at = null,
        task_type = null, recurrence = null, priority = null, children = children.toList(),
        depends_on = null, blocks = null, blocked = null,
    )

    private fun view(
        tasks: List<TaskByDueDateResponse>,
        filters: BoardFilters = BoardFilters(),
        tree: List<ActiveTreeNode> = emptyList(),
        pending: Set<Int> = emptySet(),
    ) = buildDueSoonView(tasks, tree, filters, pending, today)

    private fun DueSoonView.ids() = groups.flatMap { g -> g.tasks.map { it.id } }

    @Test
    fun `the priority filter keeps tasks at or above the floor`() {
        val tasks = listOf(task(1, priority = 1), task(2, priority = 3), task(3, priority = 5))
        assertEquals(listOf(1, 2), view(tasks, BoardFilters(duePriority = 3)).ids())
    }

    @Test
    fun `the project filter includes sub-projects and drops taskless-project tasks`() {
        val tree = listOf(project(10, project(11)), project(20))
        val tasks = listOf(task(1, projectId = 10), task(2, projectId = 11), task(3, projectId = 20), task(4, projectId = null))
        assertEquals(listOf(1, 2), view(tasks, BoardFilters(dueProject = 10), tree).ids().sorted())
    }

    @Test
    fun `a task finished a moment ago is hidden before the re-read confirms it`() {
        assertEquals(listOf(2), view(listOf(task(1), task(2)), pending = setOf(1)).ids())
    }

    @Test
    fun `the count next to the title is what is due today or earlier`() {
        val tasks = listOf(task(1, "2026-08-20T00:00:00Z"), task(2, "2026-08-29T00:00:00Z"), task(3, "2026-08-30T00:00:00Z"), task(4))
        assertEquals(2, view(tasks).dueTodayCount)
    }

    @Test
    fun `the list folds at the limit and reports what is left`() {
        val tasks = (1..12).map { task(it, "2026-09-${10 + it}T00:00:00Z") }
        val v = view(tasks)
        assertEquals(DUE_FOLD_LIMIT, v.shown)
        assertEquals(12, v.total)
        assertTrue(v.hasMore)
        assertEquals(4, v.remaining)
    }

    @Test
    fun `showing more reveals the rest`() {
        val tasks = (1..12).map { task(it) }
        val v = view(tasks, BoardFilters(dueVisibleCount = DUE_FOLD_LIMIT + DUE_EXPAND_STEP))
        assertEquals(12, v.shown)
        assertFalse(v.hasMore)
    }

    @Test
    fun `folding cannot cut off an urgent task that plain date order would rank last`() {
        // Ten later-dated tasks, then one urgent one due furthest out. Tiering first means the
        // urgent task is in the visible slice regardless of where its date would sort.
        val later = (1..10).map { task(it, "2026-10-${10 + it}T00:00:00Z") }
        val urgent = task(99, "2026-12-31T00:00:00Z").copy(urgent = true, start_by = "2026-08-29", remaining_hours = "4")
        assertTrue(99 in view(later + urgent).ids())
    }

    @Test
    fun `changing a filter puts the fold back`() {
        val expanded = BoardFilters(dueVisibleCount = 24)
        assertEquals(DUE_FOLD_LIMIT, expanded.withDuePriority(2).dueVisibleCount)
        assertEquals(DUE_FOLD_LIMIT, expanded.withDueProject(5).dueVisibleCount)
        assertEquals(24, expanded.withTreePriority(2).dueVisibleCount)
    }
}
