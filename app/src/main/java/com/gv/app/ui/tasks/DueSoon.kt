package com.gv.app.ui.tasks

import com.gv.app.domain.model.TaskByDueDateResponse
import java.time.LocalDate
import java.util.Locale

/**
 * The Due Soon list's urgency tiers. Mirrors gv-web's `dueSoonGrouping.ts` so the two lists
 * order the same tasks the same way: urgency changes a task's *position*, not just its colour.
 */
enum class DueSoonTier(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Start Today"),
    WEEK("This Week"),
    LATER("Later"),
}

data class DueSoonGroup(val tier: DueSoonTier, val tasks: List<TaskByDueDateResponse>)

private const val WEEK_HORIZON_DAYS = 7L

/** Missing dates sort last within a tier without dropping the task. */
private const val NO_DATE_SORT_KEY = "9999-99-99"

/**
 * A task's real date as `YYYY-MM-DD`: its own due date, else its project's. Read off the
 * string rather than parsed — `due_at` is a conceptual date, not an instant, so a timezone
 * conversion here would shift it a day.
 */
fun dueDateOf(t: TaskByDueDateResponse): String? =
    (t.due_at ?: t.project_due_at)?.takeIf { it.length >= 10 }?.substring(0, 10)

/** `start_by` when the task has one (it carries an estimate), otherwise its real date. */
private fun effectiveDate(t: TaskByDueDateResponse): String? = t.start_by ?: dueDateOf(t)

private fun tierFor(t: TaskByDueDateResponse, today: String, weekEdge: String): DueSoonTier {
    val actual = dueDateOf(t)
    if (actual != null && actual < today) return DueSoonTier.OVERDUE
    if (t.urgent || actual == today) return DueSoonTier.TODAY
    val effective = effectiveDate(t)
    if (effective != null && effective <= weekEdge) return DueSoonTier.WEEK
    return DueSoonTier.LATER
}

private fun byDateThenPriority(dateOf: (TaskByDueDateResponse) -> String?) =
    Comparator<TaskByDueDateResponse> { a, b ->
        val da = dateOf(a) ?: NO_DATE_SORT_KEY
        val db = dateOf(b) ?: NO_DATE_SORT_KEY
        if (da != db) da.compareTo(db) else a.priority.compareTo(b.priority)
    }

/**
 * Splits Due Soon into four tiers. `OVERDUE` / `TODAY` sort by the real due date (what is
 * actually closest); `WEEK` / `LATER` sort by the effective date. A task due today always
 * lands in `TODAY`, estimate or not: the estimate only ever promotes a task *before* its due
 * date (the `urgent` early warning), it never demotes one that is due now.
 *
 * Always returns all four tiers, in order, empty ones included.
 */
fun groupTasksByUrgency(
    tasks: List<TaskByDueDateResponse>,
    today: LocalDate = LocalDate.now(),
): List<DueSoonGroup> {
    val todayKey = today.toString()
    val weekEdge = today.plusDays(WEEK_HORIZON_DAYS).toString()
    val buckets = tasks.groupBy { tierFor(it, todayKey, weekEdge) }

    fun bucket(tier: DueSoonTier, cmp: Comparator<TaskByDueDateResponse>) =
        DueSoonGroup(tier, buckets[tier].orEmpty().sortedWith(cmp))

    val byActual = byDateThenPriority(::dueDateOf)
    val byEffective = byDateThenPriority(::effectiveDate)
    return listOf(
        bucket(DueSoonTier.OVERDUE, byActual),
        bucket(DueSoonTier.TODAY, byActual),
        bucket(DueSoonTier.WEEK, byEffective),
        bucket(DueSoonTier.LATER, byEffective),
    )
}

/** Cuts a tier-ordered list to a total task budget, dropping tiers it empties. */
fun truncateDueSoonGroups(groups: List<DueSoonGroup>, limit: Int): List<DueSoonGroup> {
    val out = mutableListOf<DueSoonGroup>()
    var remaining = limit
    for (g in groups) {
        if (remaining <= 0) break
        if (g.tasks.isEmpty()) continue
        val slice = g.tasks.take(remaining)
        out.add(g.copy(tasks = slice))
        remaining -= slice.size
    }
    return out
}

private fun formatHoursShort(hours: Double): String =
    if (hours % 1.0 == 0.0) "${hours.toInt()}h" else String.format(Locale.ROOT, "%.1fh", hours)

/**
 * One line saying why an urgent task is urgent — "6h left · should've started 2d ago" — so the
 * reason is on screen instead of being left to colour. Null when there is nothing to say (no
 * estimate, or not urgent).
 */
fun buildUrgencyPhrase(t: TaskByDueDateResponse, today: LocalDate = LocalDate.now()): String? {
    if (!t.urgent) return null
    val remaining = t.remaining_hours?.toDoubleOrNull() ?: return null
    val startBy = t.start_by ?: return null

    val hoursLabel = "${formatHoursShort(remaining)} left"
    val todayKey = today.toString()
    if (startBy >= todayKey) return "$hoursLabel · start today"

    val daysLate = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(startBy), today)
    val startLabel = if (daysLate == 1L) "should've started yesterday" else "should've started ${daysLate}d ago"
    return "$hoursLabel · $startLabel"
}
