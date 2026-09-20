package com.gv.app.ui.tasks

import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.TaskByDueDateResponse
import java.time.LocalDate

/**
 * Deliberately smaller than the usual 15/10 fold: a Due Soon card carries badges and an urgency
 * line, and the list is tiered, so "today + this week" should fit without much scrolling and
 * "later" is one tap away instead of pre-rendered. Same numbers as gv-web's `TaskBoard`.
 */
const val DUE_FOLD_LIMIT = 8
const val DUE_EXPAND_STEP = 8

/** What the user has narrowed the lists to. Every change resets the fold. */
data class BoardFilters(
    val duePriority: Int? = null,
    val dueProject: Int? = null,
    val treePriority: Int? = null,
    val dueVisibleCount: Int = DUE_FOLD_LIMIT,
) {
    fun withDuePriority(value: Int?) = copy(duePriority = value, dueVisibleCount = DUE_FOLD_LIMIT)
    fun withDueProject(value: Int?) = copy(dueProject = value, dueVisibleCount = DUE_FOLD_LIMIT)
    fun withTreePriority(value: Int?) = copy(treePriority = value)
}

/** Due Soon, filtered, tiered and folded — everything the section renders. */
data class DueSoonView(
    val groups: List<DueSoonGroup>,
    /** Tasks matching the filters, before folding. */
    val total: Int,
    /** Tasks due today or earlier — the count next to the title. */
    val dueTodayCount: Int,
    val projectOptions: List<ProjectOption>,
) {
    val shown: Int get() = groups.sumOf { it.tasks.size }
    val hasMore: Boolean get() = shown < total
    val remaining: Int get() = total - shown
}

/**
 * Applies the priority / project filters to Due Soon, tiers it by urgency and folds it.
 * Tiering happens *before* folding so an urgent task ranked low in plain date order is not
 * cut off by the fold.
 *
 * [pendingTasks] are ones finished a moment ago and waiting on the re-read that confirms it.
 */
fun buildDueSoonView(
    tasks: List<TaskByDueDateResponse>,
    tree: List<ActiveTreeNode>,
    filters: BoardFilters,
    pendingTasks: Set<Int>,
    today: LocalDate = LocalDate.now(),
): DueSoonView {
    val projectIds = filters.dueProject?.let { collectProjectIds(tree, it) }
    val filtered = tasks
        .asSequence()
        .filter { filters.duePriority == null || it.priority <= filters.duePriority }
        .filter { it.id !in pendingTasks }
        .filter { projectIds == null || (it.project_id != null && it.project_id in projectIds) }
        .toList()

    val todayKey = today.toString()
    return DueSoonView(
        groups = truncateDueSoonGroups(groupTasksByUrgency(filtered, today), filters.dueVisibleCount),
        total = filtered.size,
        dueTodayCount = filtered.count { t -> dueDateOf(t)?.let { it <= todayKey } == true },
        projectOptions = flattenProjects(tree),
    )
}
