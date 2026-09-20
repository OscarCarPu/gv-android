package com.gv.app.ui.tasks

import com.gv.app.domain.model.ActiveTreeNode

/** A project and how deeply it is nested, for an indented project picker. */
data class ProjectOption(val id: Int, val name: String, val depth: Int)

/** Depth-first list of every project in the tree. */
fun flattenProjects(nodes: List<ActiveTreeNode>, depth: Int = 0): List<ProjectOption> {
    val out = mutableListOf<ProjectOption>()
    for (n in nodes) {
        if (n.type != "project") continue
        out.add(ProjectOption(n.id, n.name, depth))
        n.children?.let { out.addAll(flattenProjects(it, depth + 1)) }
    }
    return out
}

/** [targetId] and every project below it, so filtering by a project includes its sub-projects. */
fun collectProjectIds(nodes: List<ActiveTreeNode>, targetId: Int): Set<Int> {
    val ids = mutableSetOf<Int>()

    fun gather(n: ActiveTreeNode) {
        if (n.type != "project") return
        ids.add(n.id)
        n.children?.forEach(::gather)
    }

    fun find(ns: List<ActiveTreeNode>): Boolean {
        for (n in ns) {
            if (n.type == "project" && n.id == targetId) {
                gather(n)
                return true
            }
            if (n.children != null && find(n.children)) return true
        }
        return false
    }

    find(nodes)
    return ids
}

/**
 * The tree with hidden nodes removed and tasks below the priority floor dropped. Projects are
 * always kept whatever their children's priorities are, matching the API's `min_priority`.
 *
 * [pendingTasks] / [pendingProjects] are ones finished a moment ago and awaiting the re-read
 * that confirms it; they are hidden straight away so the tap feels immediate.
 */
fun filterTree(
    nodes: List<ActiveTreeNode>,
    minPriority: Int?,
    pendingTasks: Set<Int>,
    pendingProjects: Set<Int>,
): List<ActiveTreeNode> {
    val out = mutableListOf<ActiveTreeNode>()
    for (n in nodes) {
        if (n.type == "project") {
            if (n.id in pendingProjects) continue
            out.add(n.copy(children = n.children?.let { filterTree(it, minPriority, pendingTasks, pendingProjects) }))
        } else {
            if (n.id in pendingTasks) continue
            if (minPriority != null && (n.priority ?: 3) > minPriority) continue
            out.add(n)
        }
    }
    return out
}

/** Pure lookup used by the toggle handlers to read a node's current type/recurrence. */
fun findTreeTask(nodes: List<ActiveTreeNode>, id: Int): ActiveTreeNode? {
    for (n in nodes) {
        if (n.type == "task" && n.id == id) return n
        n.children?.let { c -> findTreeTask(c, id)?.let { return it } }
    }
    return null
}
