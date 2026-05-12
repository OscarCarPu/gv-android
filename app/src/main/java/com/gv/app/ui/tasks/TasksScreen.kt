package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.PlanTodayResponse
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

private enum class TasksTab(val label: String) {
    TODAY("Today"),
    DUE("Due"),
    PROJECTS("Projects"),
}

@Composable
fun TasksScreen(vm: TasksViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val editingDetail by vm.editingDetail.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(TasksTab.TODAY) }
    var priorityFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(vm) {
        vm.toast.collect { msg -> snackbar.showSnackbar(msg) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TimerCard(
                timer = timer,
                onStop = { showStopDialog = true },
                onCancel = vm::cancelTimer,
                onCommentChange = vm::updateTimerComment,
            )
            TabBar(selected = tab, onSelect = { tab = it })

            when (val s = state) {
                is TasksUiState.Loading -> CenteredLoader()
                is TasksUiState.Error -> ErrorState(s.message, vm::refresh)
                is TasksUiState.Loaded -> when (tab) {
                    TasksTab.TODAY -> TodayTab(
                        data = s.data,
                        timerRunning = timer.isRunning,
                        onTaskDetail = vm::openDetail,
                        onTaskStartAction = vm::startTask,
                        onTaskFinishOrRenew = vm::finishTaskOrRenew,
                        onTimerStart = vm::startOrAssignTimer,
                    )
                    TasksTab.DUE -> DueTab(
                        all = s.data.byDueDate,
                        priorityFilter = priorityFilter,
                        onPriorityFilter = { priorityFilter = it },
                        timerRunning = timer.isRunning,
                        onTaskDetail = vm::openDetail,
                        onTaskStartAction = vm::startTask,
                        onTaskFinishOrRenew = vm::finishTaskOrRenew,
                        onTimerStart = vm::startOrAssignTimer,
                    )
                    TasksTab.PROJECTS -> ProjectsTab(
                        nodes = s.data.tree,
                        onTaskDetail = vm::openDetail,
                    )
                }
            }
        }

        if (state is TasksUiState.Loaded) {
            FloatingActionButton(
                onClick = { showCreate = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 0.dp),
                containerColor = GvColors.Primary,
                contentColor = GvColors.Text,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New task")
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = GvColors.Surface,
                contentColor = GvColors.Text,
            )
        }
    }

    editingDetail?.let { task ->
        TaskDetailSheet(
            task = task,
            onDismiss = vm::closeDetail,
            onSave = vm::saveTaskDetail,
            onDelete = { pendingDelete = it },
            onStart = { vm.startTask(task.id) },
            onFinish = { vm.finishTaskOrRenew(taskToByDue(task)) },
            onTimerStart = { vm.startOrAssignTimer(task.id) },
            onAddTodo = vm::addTodo,
            onToggleTodo = vm::toggleTodo,
            onDeleteTodo = vm::deleteTodo,
            timerRunning = timer.isRunning,
        )
    }

    if (showCreate) {
        val data = (state as? TasksUiState.Loaded)?.data
        TaskCreateSheet(
            projects = data?.projects.orEmpty(),
            onDismiss = { showCreate = false },
            onCreate = { req, startNow ->
                vm.createTask(req, startNow) { ok -> if (ok) showCreate = false }
            },
        )
    }

    if (showStopDialog) {
        StopTimerDialog(
            initialComment = timer.active?.comment.orEmpty(),
            onDismiss = { showStopDialog = false },
            onStop = { comment ->
                vm.stopTimer(comment)
                showStopDialog = false
            },
        )
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = GvColors.BgLight,
            title = { Text("Delete task?", color = GvColors.Text) },
            text = {
                Text(
                    "This task, its todos and time entries will be removed. This cannot be undone.",
                    color = GvColors.TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteTask(id); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GvColors.Danger,
                        contentColor = GvColors.Text,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
            },
        )
    }
}

// Shape adapter so TaskDetailSheet's "finish" can hit recurring renewal logic
private fun taskToByDue(t: com.gv.app.domain.model.TaskFullResponse): TaskByDueDateResponse =
    TaskByDueDateResponse(
        id = t.id,
        name = t.name,
        description = t.description,
        due_at = t.due_at,
        started_at = t.started_at,
        task_type = t.task_type,
        recurrence = t.recurrence,
        priority = t.priority,
        time_spent = t.time_spent,
        project_id = t.project_id,
        project_name = null,
        project_due_at = null,
        depends_on = t.depends_on,
        blocks = t.blocks,
        blocked = t.blocked,
    )

@Composable
private fun TabBar(selected: TasksTab, onSelect: (TasksTab) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.BgLight)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TasksTab.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) GvColors.Primary else GvColors.BorderLight,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(entry) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) GvColors.Primary else GvColors.TextMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------- Today tab ----------

@Composable
private fun TodayTab(
    data: TasksData,
    timerRunning: Boolean,
    onTaskDetail: (Int) -> Unit,
    onTaskStartAction: (Int) -> Unit,
    onTaskFinishOrRenew: (TaskByDueDateResponse) -> Unit,
    onTimerStart: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    val todayKey = java.time.LocalDate.now().toString()
    val today = data.byDueDate.filter {
        val key = isoDateKey(it.due_at ?: it.project_due_at)
        key != "no-date" && key <= todayKey
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        ProgressSummary(data.summary)

        if (!data.plan?.blocks.isNullOrEmpty()) {
            SectionTitle("Plan today")
            data.plan!!.blocks.forEach { block ->
                PlanBlockRow(block, onTaskDetail)
            }
        }

        SectionTitle("Due today / overdue (${today.size})")
        if (today.isEmpty()) {
            EmptyHint("Nothing due today")
        } else {
            today.forEach { task ->
                TaskRow(
                    task = task,
                    timerRunning = timerRunning,
                    onDetail = { onTaskDetail(task.id) },
                    onStartAction = { onTaskStartAction(task.id) },
                    onFinishAction = { onTaskFinishOrRenew(task) },
                    onTimerStart = { onTimerStart(task.id) },
                )
            }
        }
    }
}

@Composable
private fun ProgressSummary(summary: TimeEntrySummaryResponse) {
    val spacing = LocalSpacing.current
    val dailyTarget = summary.daily_target_seconds.coerceAtLeast(1L)
    val weeklyTarget = summary.weekly_target_seconds.coerceAtLeast(1L)
    val todayProgress = (summary.today.toFloat() / dailyTarget).coerceIn(0f, 1f)
    val weekProgress = (summary.week.toFloat() / weeklyTarget).coerceIn(0f, 1f)
    val todayColor = when {
        summary.today >= dailyTarget * 11 / 12 -> GvColors.Success
        summary.today >= dailyTarget * 5 / 6 -> GvColors.Warning
        else -> GvColors.Primary
    }
    val weekColor = if (summary.week >= weeklyTarget) GvColors.Success else GvColors.Primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        ProgressRow(
            label = "Today",
            progress = todayProgress,
            color = todayColor,
            value = "${formatDurationShort(summary.today)} / ${formatDurationShort(summary.daily_target_seconds)}",
        )
        ProgressRow(
            label = "Week",
            progress = weekProgress,
            color = weekColor,
            value = "${formatDurationShort(summary.week)} / ${formatDurationShort(summary.weekly_target_seconds)}",
        )
    }
}

@Composable
private fun ProgressRow(label: String, progress: Float, color: Color, value: String) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
            modifier = Modifier.width(48.dp),
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape),
            color = color,
            trackColor = GvColors.Border,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.Text,
        )
    }
}

@Composable
private fun PlanBlockRow(block: PlanBlockResponse, onTaskDetail: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    val start = parseIso(block.started_at)
    val end = parseIso(block.ended_at)
    val timeText = if (start != null && end != null) {
        "%02d:%02d–%02d:%02d".format(
            java.util.Locale.UK,
            start.hour, start.minute, end.hour, end.minute,
        )
    } else "—"
    val titleColor = if (block.task_finished_at != null) GvColors.TextMuted else GvColors.Text
    val isLinked = block.task_id != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .clickable(enabled = isLinked) { block.task_id?.let(onTaskDetail) }
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = if (isLinked) GvColors.Primary else GvColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                color = GvColors.TextMuted,
            )
            Text(
                text = block.task_name ?: block.label.ifBlank { "Free" },
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                textDecoration = if (block.task_finished_at != null) TextDecoration.LineThrough else null,
            )
        }
    }
}

// ---------- Due tab ----------

@Composable
private fun DueTab(
    all: List<TaskByDueDateResponse>,
    priorityFilter: Int?,
    onPriorityFilter: (Int?) -> Unit,
    timerRunning: Boolean,
    onTaskDetail: (Int) -> Unit,
    onTaskStartAction: (Int) -> Unit,
    onTaskFinishOrRenew: (TaskByDueDateResponse) -> Unit,
    onTimerStart: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    val filtered = if (priorityFilter == null) all else all.filter { it.priority <= priorityFilter }
    val today = java.time.LocalDate.now().toString()

    Column(modifier = Modifier.fillMaxSize()) {
        PriorityFilterBar(selected = priorityFilter, onSelect = onPriorityFilter)
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No tasks with this priority",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                var lastKey = ""
                filtered.forEachIndexed { idx, task ->
                    val key = isoDateKey(task.due_at ?: task.project_due_at)
                    if (key != lastKey) {
                        val labelKey = key
                        item(key = "div-$idx-$key") {
                            DayDivider(
                                label = formatRelativeDay(task.due_at ?: task.project_due_at),
                                highlight = labelKey == today,
                            )
                        }
                        lastKey = key
                    }
                    item(key = "tk-${task.id}") {
                        TaskRow(
                            task = task,
                            timerRunning = timerRunning,
                            onDetail = { onTaskDetail(task.id) },
                            onStartAction = { onTaskStartAction(task.id) },
                            onFinishAction = { onTaskFinishOrRenew(task) },
                            onTimerStart = { onTimerStart(task.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun PriorityFilterBar(selected: Int?, onSelect: (Int?) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.Bg)
            .padding(horizontal = spacing.xl, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        FilterChip("All", selected == null) { onSelect(null) }
        (1..4).forEach { p ->
            FilterChip("≤$p", selected == p) { onSelect(p) }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else GvColors.BgLight)
            .border(
                1.dp,
                if (active) GvColors.Primary else GvColors.BorderLight,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DayDivider(label: String, highlight: Boolean) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.md, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(GvColors.BorderLight)
                .size(width = 0.dp, height = 1.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = spacing.md),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .background(GvColors.BorderLight)
                .size(width = 0.dp, height = 1.dp),
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskByDueDateResponse,
    timerRunning: Boolean,
    onDetail: () -> Unit,
    onStartAction: () -> Unit,
    onFinishAction: () -> Unit,
    onTimerStart: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val started = !task.started_at.isNullOrBlank()
    val overdue = isOverdue(task.due_at ?: task.project_due_at)
    val statusColor = taskTypeColor(task.task_type, started)
    val pColor = priorityColor(task.priority)
    val borderColor = if (overdue) GvColors.Danger.copy(alpha = 0.6f) else GvColors.BorderLight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onDetail() }
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (task.blocked) {
                Icon(
                    Icons.Filled.Block,
                    contentDescription = "Blocked",
                    tint = GvColors.Danger,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(spacing.xs))
            }
            PriorityBadge(task.priority, pColor)
        }

        task.project_name?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.TextMuted,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(top = spacing.xs),
        ) {
            StatusBadge(
                label = statusLabel(task.started_at, task.task_type, task.recurrence),
                color = statusColor,
            )
            task.due_at?.let {
                MetaPill(formatShortDate(it), GvColors.TextMuted)
            }
            if (task.time_spent > 0) {
                MetaPill(formatDurationShort(task.time_spent), GvColors.TextMuted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (started) {
                OutlinedButton(
                    onClick = onFinishAction,
                    enabled = !task.blocked,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (task.task_type == "recurring") "Renew" else "Finish",
                        color = if (task.blocked) GvColors.TextMuted else GvColors.Primary,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onStartAction,
                    enabled = !task.blocked,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Start",
                        color = if (task.blocked) GvColors.TextMuted else GvColors.Primary,
                    )
                }
            }
            Button(
                onClick = onTimerStart,
                enabled = !task.blocked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                    disabledContainerColor = GvColors.Border,
                    disabledContentColor = GvColors.TextMuted,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (timerRunning) "Assign" else "Timer")
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "P$priority",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GvColors.Bg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ---------- Projects tab ----------

@Composable
private fun ProjectsTab(
    nodes: List<ActiveTreeNode>,
    onTaskDetail: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    if (nodes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(spacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No active projects",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }
        return
    }
    val collapsed = rememberSaveable(saver = collapsedSaver()) {
        mutableStateOf(emptySet<Int>())
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        val flat = flattenTree(nodes, collapsed.value, depth = 0)
        items(flat, key = { "${it.node.type}-${it.node.id}-${it.depth}" }) { entry ->
            TreeRow(
                entry = entry,
                isCollapsed = entry.node.id in collapsed.value,
                onToggle = {
                    collapsed.value = collapsed.value.toMutableSet().apply {
                        if (!add(entry.node.id)) remove(entry.node.id)
                    }
                },
                onTaskDetail = onTaskDetail,
            )
        }
    }
}

private data class TreeEntry(val node: ActiveTreeNode, val depth: Int)

private fun flattenTree(
    nodes: List<ActiveTreeNode>,
    collapsed: Set<Int>,
    depth: Int,
): List<TreeEntry> {
    val out = mutableListOf<TreeEntry>()
    for (n in nodes) {
        out.add(TreeEntry(n, depth))
        if (n.type == "project" && n.id !in collapsed && !n.children.isNullOrEmpty()) {
            out.addAll(flattenTree(n.children, collapsed, depth + 1))
        }
    }
    return out
}

@Composable
private fun TreeRow(
    entry: TreeEntry,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onTaskDetail: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    val n = entry.node
    val indent = (entry.depth * 20).dp
    val isProject = n.type == "project"
    val started = !n.started_at.isNullOrBlank()
    val statusColor = if (isProject) {
        if (started) GvColors.Primary else GvColors.TextMuted
    } else {
        taskTypeColor(n.task_type, started)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .clickable(enabled = !isProject) { onTaskDetail(n.id) }
            .padding(start = indent, end = spacing.md)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (isProject) {
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isCollapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = n.name,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                fontWeight = if (isProject) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (!isProject) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    StatusBadge(
                        label = statusLabel(n.started_at, n.task_type, n.recurrence),
                        color = statusColor,
                    )
                    n.priority?.let {
                        PriorityBadge(it, priorityColor(it))
                    }
                    n.due_at?.let {
                        MetaPill(formatShortDate(it), GvColors.TextMuted)
                    }
                }
            } else {
                n.due_at?.let {
                    Text(
                        text = "Due ${formatShortDate(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.TextMuted,
                    )
                }
            }
        }
        if (n.blocked == true) {
            Icon(
                Icons.Filled.Block,
                contentDescription = "Blocked",
                tint = GvColors.Danger,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun collapsedSaver(): androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<Set<Int>>, ArrayList<Int>> =
    androidx.compose.runtime.saveable.Saver(
        save = { ArrayList(it.value) },
        restore = { androidx.compose.runtime.mutableStateOf(it.toSet()) },
    )

// ---------- Timer card ----------

@Composable
private fun TimerCard(
    timer: TimerState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onCommentChange: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val active = timer.active

    if (active == null) {
        // Idle state — compact strip with a hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GvColors.BgLight)
                .padding(horizontal = spacing.xl, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = GvColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "No timer running",
                style = MaterialTheme.typography.labelMedium,
                color = GvColors.TextMuted,
            )
        }
        return
    }

    var comment by remember(active.id) { mutableStateOf(active.comment.orEmpty()) }
    LaunchedEffect(comment) {
        kotlinx.coroutines.delay(500)
        onCommentChange(comment)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.Primary.copy(alpha = 0.4f))
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = active.task_name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.Text,
                    fontWeight = FontWeight.SemiBold,
                )
                active.project_name?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.TextMuted,
                    )
                }
            }
            Text(
                text = formatHhMmSs(timer.elapsedSeconds),
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("Comment...", color = GvColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    cursorColor = GvColors.Primary,
                ),
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = GvColors.TextMuted,
                )
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Danger,
                    contentColor = GvColors.Text,
                ),
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun StopTimerDialog(
    initialComment: String,
    onDismiss: () -> Unit,
    onStop: (String?) -> Unit,
) {
    var comment by remember { mutableStateOf(initialComment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GvColors.BgLight,
        title = { Text("Stop timer?", color = GvColors.Text) },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Comment (optional)") },
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    focusedLabelColor = GvColors.Primary,
                    unfocusedLabelColor = GvColors.TextMuted,
                    cursorColor = GvColors.Primary,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = { onStop(comment) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
            ) { Text("Stop") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GvColors.TextMuted)
            }
        },
    )
}

// ---------- Shared ----------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = GvColors.Text,
    )
}

@Composable
private fun EmptyHint(text: String) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )
    }
}

@Composable
private fun CenteredLoader() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GvColors.Primary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
        OutlinedButton(onClick = onRetry) {
            Text("Retry", color = GvColors.Primary)
        }
    }
}

