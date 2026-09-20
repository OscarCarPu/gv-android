package com.gv.app.ui.tasks

import com.gv.app.ui.common.SmallButton
import com.gv.app.ui.common.FilterChip
import com.gv.app.ui.common.EmptyHint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate

/** What a Due Soon row can ask for. One bundle so the section does not thread eight lambdas. */
internal class TaskRowActions(
    val onDetail: (taskId: Int) -> Unit,
    val onStart: (taskId: Int) -> Unit,
    val onFinish: (task: TaskByDueDateResponse) -> Unit,
    val onStartTimer: (taskId: Int) -> Unit,
    val onAssignTimer: (taskId: Int) -> Unit,
    val onStopAndStart: (taskId: Int) -> Unit,
)

private fun tierColor(tier: DueSoonTier): Color = when (tier) {
    DueSoonTier.OVERDUE -> GvColors.Danger
    DueSoonTier.TODAY -> GvColors.Warning
    DueSoonTier.WEEK -> GvColors.Primary
    DueSoonTier.LATER -> GvColors.TextMuted
}

/**
 * Due Soon as web has it: tiered by urgency (Overdue / Start Today / This Week / Later), with a
 * priority filter, a project filter and a fold. Emitted straight into the Today tab's list so
 * it and the plan below it scroll as one page.
 */
internal fun LazyListScope.dueSoonSection(
    view: DueSoonView,
    filters: BoardFilters,
    timerRunning: Boolean,
    actions: TaskRowActions,
    onDuePriority: (Int?) -> Unit,
    onDueProject: (Int?) -> Unit,
    onShowMore: () -> Unit,
    onNewTask: () -> Unit,
) {
    item(key = "due-header") {
        DueSoonHeader(view, filters, onDuePriority, onDueProject, onNewTask)
    }

    if (view.groups.isEmpty()) {
        item(key = "due-empty") {
            EmptyHint(if (filters.duePriority != null || filters.dueProject != null) "Nothing matches these filters" else "Nothing due soon")
        }
        return
    }

    val today = LocalDate.now()
    val todayKey = today.toString()
    for (group in view.groups) {
        item(key = "tier-${group.tier}") {
            Text(
                text = group.tier.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = tierColor(group.tier),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = LocalSpacing.current.md),
            )
        }
        group.tasks.forEachIndexed { i, task ->
            val dateKey = dueDateOf(task)
            val prevKey = group.tasks.getOrNull(i - 1)?.let(::dueDateOf)
            // A day divider wherever the date changes, and one at the top of a tier that opens
            // on today so "today" is always labelled.
            if ((i > 0 && dateKey != prevKey) || (i == 0 && dateKey == todayKey)) {
                item(key = "day-${group.tier}-$i") {
                    DayDivider(
                        label = formatRelativeDay(task.due_at ?: task.project_due_at),
                        highlight = dateKey == todayKey,
                    )
                }
            }
            item(key = "due-${task.id}") {
                DueTaskRow(
                    task = task,
                    timerRunning = timerRunning,
                    today = today,
                    onDetail = { actions.onDetail(task.id) },
                    onStart = { actions.onStart(task.id) },
                    onFinish = { actions.onFinish(task) },
                    onStartTimer = { actions.onStartTimer(task.id) },
                    onAssignTimer = { actions.onAssignTimer(task.id) },
                    onStopAndStart = { actions.onStopAndStart(task.id) },
                )
            }
        }
    }

    if (view.hasMore) {
        item(key = "due-more") { ShowMore(view.remaining, onShowMore) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DueSoonHeader(
    view: DueSoonView,
    filters: BoardFilters,
    onDuePriority: (Int?) -> Unit,
    onDueProject: (Int?) -> Unit,
    onNewTask: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text("Due Soon", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
            Text("${view.dueTodayCount}", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
            Box(Modifier.weight(1f))
            SmallButton("Task", onNewTask, icon = Icons.Filled.Add)
        }
        // Five priority chips and the project chip are wider than a phone, so they wrap.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            PriorityFilter(selected = filters.duePriority, onSelect = onDuePriority)
            if (view.projectOptions.isNotEmpty()) {
                ProjectFilter(view.projectOptions, selected = filters.dueProject, onSelect = onDueProject)
            }
        }
    }
}

/** `All · ≤1 · ≤2 · ≤3 · ≤4`, the same pill group the web puts above both lists. */
@Composable
internal fun PriorityFilter(selected: Int?, onSelect: (Int?) -> Unit) {
    val spacing = LocalSpacing.current
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        FilterChip("All", selected == null) { onSelect(null) }
        (1..4).forEach { p -> FilterChip("≤$p", selected == p) { onSelect(p) } }
    }
}

@Composable
private fun ProjectFilter(options: List<ProjectOption>, selected: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.id == selected }?.name ?: "Project"
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected != null) GvColors.Primary.copy(alpha = 0.18f) else GvColors.BgLight)
                .border(1.dp, if (selected != null) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(16.dp))
                .clickable { open = true }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected != null) GvColors.Primary else GvColors.TextMuted,
                maxLines = 1,
                modifier = Modifier.padding(end = 2.dp),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = GvColors.BgLight) {
            DropdownMenuItem(
                text = { Text("All projects", color = GvColors.TextMuted) },
                onClick = { onSelect(null); open = false },
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text("  ".repeat(opt.depth) + opt.name, color = GvColors.Text) },
                    onClick = { onSelect(opt.id); open = false },
                )
            }
        }
    }
}

/** The line — pill — line control that unfolds the next batch, as on the web. */
@Composable
internal fun ShowMore(remaining: Int, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = GvColors.BorderLight)
        Row(
            modifier = Modifier
                .padding(horizontal = spacing.md)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = GvColors.TextMuted, modifier = Modifier.size(16.dp))
            Text("$remaining more", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = GvColors.BorderLight)
    }
}
