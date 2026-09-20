package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/** What a tree row can ask for; one bundle instead of a lambda per button. */
internal class TreeActions(
    val onDetail: (taskId: Int) -> Unit,
    val onNewTask: (projectId: Int) -> Unit,
    /** [finish] false = start; true = done, or renew for a recurring task. */
    val onToggle: (id: Int, type: String, finish: Boolean) -> Unit,
    val onStartTimer: (taskId: Int) -> Unit,
    val onAssignTimer: (taskId: Int) -> Unit,
    val onStopAndStart: (taskId: Int) -> Unit,
)

private data class TreeEntry(val node: ActiveTreeNode, val depth: Int, val parentName: String?)

/** The visible rows: a project's children only appear once it is expanded. */
private fun flattenTree(nodes: List<ActiveTreeNode>, expanded: Set<Int>, depth: Int, parentName: String?): List<TreeEntry> {
    val out = mutableListOf<TreeEntry>()
    for (n in nodes) {
        out.add(TreeEntry(n, depth, parentName))
        if (n.type == "project" && n.id in expanded && !n.children.isNullOrEmpty()) {
            out.addAll(flattenTree(n.children, expanded, depth + 1, n.name))
        }
    }
    return out
}

private fun expandedSaver(): Saver<androidx.compose.runtime.MutableState<Set<Int>>, ArrayList<Int>> =
    Saver(save = { ArrayList(it.value) }, restore = { mutableStateOf(it.toSet()) })

/**
 * Active Projects, as on the web: the tree with a priority filter and the same per-row actions
 * as Due Soon — Start / Done and the timer buttons on a task; add-a-task and Done on a project.
 * Projects start collapsed; expand the ones you want to drill into.
 */
@Composable
internal fun ProjectsTab(
    nodes: List<ActiveTreeNode>,
    priority: Int?,
    timerRunning: Boolean,
    actions: TreeActions,
    onPriority: (Int?) -> Unit,
) {
    val spacing = LocalSpacing.current
    val expanded = rememberSaveable(saver = expandedSaver()) { mutableStateOf(emptySet<Int>()) }
    val flat = flattenTree(nodes, expanded.value, depth = 0, parentName = null)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        item(key = "projects-header") {
            Column(Modifier.padding(bottom = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Active Projects", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
                PriorityFilter(selected = priority, onSelect = onPriority)
            }
        }
        if (nodes.isEmpty()) {
            item(key = "projects-empty") {
                EmptyHint(if (priority != null) "Nothing matches this filter" else "No active projects")
            }
        }
        items(flat, key = { "${it.node.type}-${it.node.id}-${it.depth}" }) { entry ->
            TreeRow(
                entry = entry,
                collapsed = entry.node.id !in expanded.value,
                timerRunning = timerRunning,
                actions = actions,
                onToggleExpanded = {
                    expanded.value = expanded.value.toMutableSet().apply {
                        if (!add(entry.node.id)) remove(entry.node.id)
                    }
                },
            )
        }
    }
}

@Composable
private fun TreeRow(
    entry: TreeEntry,
    collapsed: Boolean,
    timerRunning: Boolean,
    actions: TreeActions,
    onToggleExpanded: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val n = entry.node
    val isProject = n.type == "project"
    val started = !n.started_at.isNullOrBlank()
    val recurring = n.task_type == "recurring"
    val blocked = n.blocked == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth * 16).dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tree-child left rail (style-guide parity).
        if (entry.depth > 0 && !isProject) {
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(GvColors.Primary))
        }
        Column(
            modifier = Modifier.weight(1f).padding(end = spacing.md).padding(vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (isProject) {
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                            contentDescription = if (collapsed) "Expand" else "Collapse",
                            tint = GvColors.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = GvColors.Primary, modifier = Modifier.size(16.dp))
                } else {
                    Box(Modifier.size(28.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isProject) { actions.onDetail(n.id) },
                ) {
                    Text(
                        text = n.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GvColors.Text,
                        fontWeight = if (isProject) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (isProject) {
                        n.due_at?.let { Text("Due ${formatShortDate(it)}", style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted) }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            StatusBadge(statusLabel(n.started_at, n.task_type, n.recurrence), taskTypeColor(n.task_type, started))
                            n.priority?.let { PriorityBadge(it, priorityColor(it)) }
                            n.due_at?.let { MetaPill(formatShortDate(it), GvColors.TextMuted) }
                        }
                    }
                }
                if (blocked) {
                    Icon(Icons.Filled.Block, contentDescription = "Blocked", tint = GvColors.Danger, modifier = Modifier.size(14.dp))
                }
                if (isProject) {
                    IconButton(onClick = { actions.onNewTask(n.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Add task", tint = GvColors.Primary, modifier = Modifier.size(20.dp))
                    }
                    SmallButton("Done", { actions.onToggle(n.id, "project", true) })
                }
            }

            if (!isProject) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (started) {
                        SmallButton(if (recurring) "Renew" else "Done", { actions.onToggle(n.id, "task", true) }, enabled = !blocked)
                    } else {
                        SmallButton("Start", { actions.onToggle(n.id, "task", false) }, enabled = !blocked, color = GvColors.Success)
                    }
                    TimerActions(
                        recurring = recurring,
                        blocked = blocked,
                        timerRunning = timerRunning,
                        onStart = { actions.onStartTimer(n.id) },
                        onAssign = { actions.onAssignTimer(n.id) },
                        onStopAndStart = { actions.onStopAndStart(n.id) },
                    )
                }
            }
        }
    }
}
