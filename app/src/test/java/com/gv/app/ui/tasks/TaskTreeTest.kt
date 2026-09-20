package com.gv.app.ui.tasks

import com.gv.app.domain.model.ActiveTreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskTreeTest {

    private fun node(
        id: Int,
        type: String,
        children: List<ActiveTreeNode>? = null,
        priority: Int? = null,
    ) = ActiveTreeNode(
        id = id, type = type, name = "$type $id", description = null, due_at = null, started_at = null,
        task_type = if (type == "task") "standard" else null, recurrence = null, priority = priority,
        children = children, depends_on = null, blocks = null, blocked = null,
    )

    // 1 ─┬ 2 (task, p1)
    //    ├ 3 ─┬ 4 (task, p4)
    //    │    └ 5 (project) ── 6 (task)
    //    └ 7 (project)
    private val tree = listOf(
        node(1, "project", listOf(
            node(2, "task", priority = 1),
            node(3, "project", listOf(node(4, "task", priority = 4), node(5, "project", listOf(node(6, "task"))))),
            node(7, "project", emptyList()),
        )),
    )

    @Test
    fun `projects flatten depth first with their nesting depth`() {
        assertEquals(
            listOf(ProjectOption(1, "project 1", 0), ProjectOption(3, "project 3", 1), ProjectOption(5, "project 5", 2), ProjectOption(7, "project 7", 1)),
            flattenProjects(tree),
        )
    }

    @Test
    fun `filtering by a project includes its sub-projects but not its siblings`() {
        assertEquals(setOf(3, 5), collectProjectIds(tree, 3))
        assertEquals(setOf(1, 3, 5, 7), collectProjectIds(tree, 1))
    }

    @Test
    fun `an unknown or task id collects nothing`() {
        assertEquals(emptySet<Int>(), collectProjectIds(tree, 99))
        assertEquals(emptySet<Int>(), collectProjectIds(tree, 2))
    }

    @Test
    fun `the priority floor hides low-priority tasks but never projects`() {
        val filtered = filterTree(tree, minPriority = 2, pendingTasks = emptySet(), pendingProjects = emptySet())
        val ids = mutableListOf<Int>()
        fun walk(ns: List<ActiveTreeNode>) {
            for (n in ns) {
                ids.add(n.id)
                n.children?.let { walk(it) }
            }
        }
        walk(filtered)
        // Task 4 (p4) is gone; task 6 has no priority and counts as the default 3, so it goes too.
        assertEquals(listOf(1, 2, 3, 5, 7), ids)
    }

    @Test
    fun `no floor keeps everything`() {
        assertEquals(tree, filterTree(tree, null, emptySet(), emptySet()))
    }

    @Test
    fun `pending nodes are hidden, and hiding a project hides what is inside it`() {
        val noTask = filterTree(tree, null, pendingTasks = setOf(2), pendingProjects = emptySet())
        assertEquals(listOf(3, 7), noTask.single().children!!.map { it.id })
        assertEquals(emptyList<ActiveTreeNode>(), filterTree(tree, null, emptySet(), pendingProjects = setOf(1)))
    }

    @Test
    fun `a task is found anywhere in the tree`() {
        assertEquals(6, findTreeTask(tree, 6)?.id)
        assertNull(findTreeTask(tree, 5)) // a project, not a task
        assertNull(findTreeTask(tree, 99))
    }
}
