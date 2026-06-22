package com.gv.app.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.data.repository.TasksData
import com.gv.app.domain.model.ActiveTreeNode
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import com.gv.app.ui.theme.TimerDisplay

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
    var showTimerSheet by remember { mutableStateOf(false) }
    var showAgenda by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }

    // Swipe left/right to change sub-tab, kept in sync with the bottom-bar selection.
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { TasksTab.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> tab = TasksTab.entries[page] }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal) pagerState.animateScrollToPage(tab.ordinal)
    }

    LaunchedEffect(vm) {
        vm.toast.collect { msg -> snackbar.showSnackbar(msg) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (val s = state) {
                    is TasksUiState.Loading -> CenteredLoader()
                    is TasksUiState.Loaded -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { it },
                    ) { page ->
                        when (TasksTab.entries[page]) {
                            TasksTab.TODAY -> TodayTab(s.data, onTaskDetail = vm::openDetail)
                            TasksTab.DUE -> DueTab(
                                all = s.data.byDueDate,
                                priorityFilter = priorityFilter,
                                onTaskDetail = vm::openDetail,
                            )
                            TasksTab.PROJECTS -> ProjectsTab(s.data.tree, onTaskDetail = vm::openDetail)
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { data ->
                    Snackbar(snackbarData = data, containerColor = GvColors.Surface, contentColor = GvColors.Text)
                }
            }

            // Priority filter rides just above the bottom bar, only on the Due tab.
            AnimatedVisibility(
                visible = state is TasksUiState.Loaded && tab == TasksTab.DUE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PriorityFilterBar(selected = priorityFilter, onSelect = { priorityFilter = it })
            }

            TasksBottomBar(
                selected = tab,
                onSelect = { tab = it },
                timer = timer,
                onAdd = { showCreate = true },
                onAgenda = { showAgenda = true },
                onTimerClick = { showTimerSheet = true },
            )
        }
    }

    editingDetail?.let { task ->
        TaskDetailSheet(
            task = task,
            onDismiss = vm::closeDetail,
            onSave = { id, name, desc, due, type, rec, prio, _ ->
                vm.saveTaskDetail(id, name, desc, due, type, rec, prio) { ok -> if (ok) vm.closeDetail() }
            },
            onDelete = { pendingDelete = it },
            onStart = { vm.startTask(task.id); vm.closeDetail() },
            onFinish = { vm.finishOrRenew(task.id, task.task_type, task.recurrence); vm.closeDetail() },
            onTimerStart = {
                vm.startOrAssignTimer(task.id, task.name, null, task.task_type, task.recurrence, task.priority)
                vm.closeDetail()
            },
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
            onCreate = { req, startNow -> vm.createTask(req, startNow) { ok -> if (ok) showCreate = false } },
        )
    }

    if (showTimerSheet && timer.isRunning) {
        TimerSheet(
            timer = timer,
            onCommentChange = vm::updateTimerComment,
            onEditStart = vm::editActiveTimerStart,
            onStop = { comment -> vm.stopTimer(comment); showTimerSheet = false },
            onCancel = { vm.cancelTimer(); showTimerSheet = false },
            onDismiss = { showTimerSheet = false },
        )
    }

    if (showAgenda) {
        AgendaSheet(vm = vm, onDismiss = { showAgenda = false })
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
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Danger, contentColor = GvColors.Text),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = GvColors.TextMuted) }
            },
        )
    }
}

// ---------- Bottom bar (thumb-reach: sub-tabs + add + timer) ----------

@Composable
private fun TasksBottomBar(
    selected: TasksTab,
    onSelect: (TasksTab) -> Unit,
    timer: TimerState,
    onAdd: () -> Unit,
    onAgenda: () -> Unit,
    onTimerClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(color = GvColors.BgLight, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            TasksTab.entries.forEach { entry ->
                TabChip(
                    label = entry.label,
                    active = entry == selected,
                    onClick = { onSelect(entry) },
                    modifier = Modifier.weight(1f),
                )
            }
            IconButton(onClick = onAgenda, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Agenda", tint = GvColors.TextMuted, modifier = Modifier.size(22.dp))
            }
            FilledIconButton(
                onClick = onAdd,
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
                modifier = Modifier.size(40.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "New task", modifier = Modifier.size(20.dp)) }

            if (timer.isRunning) {
                TimerPill(timer = timer, onClick = onTimerClick)
            } else {
                TimerIdleButton()
            }
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, GvColors.Primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimerPill(timer: TimerState, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Surface(
        color = GvColors.Surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .height(40.dp)
            .widthIn(max = 150.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = spacing.lg, end = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = timer.active?.taskName.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = GvColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatHhMmSs(timer.elapsedSeconds),
                    style = TimerDisplay.copy(fontSize = 15.sp),
                    color = GvColors.Primary,
                    maxLines = 1,
                )
            }
            Icon(Icons.Filled.Stop, contentDescription = "Open timer", tint = GvColors.Danger, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TimerIdleButton() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, GvColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = "No timer running", tint = GvColors.TextMuted, modifier = Modifier.size(20.dp))
    }
}

// ---------- Timer sheet (start/stop/comment within thumb reach) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerSheet(
    timer: TimerState,
    onCommentChange: (String) -> Unit,
    onEditStart: (String) -> Unit,
    onStop: (String?) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = timer.active ?: return
    var comment by remember(active.outboxId) { mutableStateOf(active.comment.orEmpty()) }
    val startedAt = remember(active.startedAt) { parseIso(active.startedAt) ?: java.time.LocalDateTime.now() }

    LaunchedEffect(comment) {
        kotlinx.coroutines.delay(500)
        onCommentChange(comment)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column {
                Text(active.taskName, style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
                active.projectName?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
                }
            }
            Text(
                text = formatHhMmSs(timer.elapsedSeconds),
                style = TimerDisplay,
                color = GvColors.Primary,
            )
            DateTimeField("Started", startedAt) { onEditStart(localDateTimeToIsoUtc(it)) }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("Comment…", color = GvColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    cursorColor = GvColors.Primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
                    Text(" Discard", color = GvColors.TextMuted)
                }
                Button(
                    onClick = { onStop(comment) },
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Danger, contentColor = GvColors.Text),
                    modifier = Modifier.weight(2f),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Stop & save")
                }
            }
        }
    }
}

// ---------- Today tab ----------

@Composable
private fun TodayTab(data: TasksData, onTaskDetail: (Int) -> Unit) {
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
        data.summary?.let { ProgressSummary(it) }

        if (!data.plan?.blocks.isNullOrEmpty()) {
            SectionTitle("Plan today")
            data.plan!!.blocks.forEach { block -> PlanBlockRow(block, onTaskDetail) }
        }

        SectionTitle("Due today / overdue (${today.size})")
        if (today.isEmpty()) {
            EmptyHint("Nothing due today")
        } else {
            today.forEach { task ->
                TaskRow(task = task, onDetail = { onTaskDetail(task.id) })
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
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        ProgressRow("Today", todayProgress, todayColor, "${formatDurationShort(summary.today)} / ${formatDurationShort(summary.daily_target_seconds)}")
        ProgressRow("Week", weekProgress, weekColor, "${formatDurationShort(summary.week)} / ${formatDurationShort(summary.weekly_target_seconds)}")
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted, modifier = Modifier.widthIn(min = 44.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
            color = color,
            trackColor = GvColors.Border,
        )
        Text(value, style = MaterialTheme.typography.labelMedium, color = GvColors.Text)
    }
}

@Composable
private fun PlanBlockRow(block: PlanBlockResponse, onTaskDetail: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    val start = parseIso(block.started_at)
    val end = parseIso(block.ended_at)
    val timeText = if (start != null && end != null) {
        "%02d:%02d–%02d:%02d".format(java.util.Locale.UK, start.hour, start.minute, end.hour, end.minute)
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
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = if (isLinked) GvColors.Primary else GvColors.TextMuted, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(timeText, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
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
    onTaskDetail: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    val filtered = if (priorityFilter == null) all else all.filter { it.priority <= priorityFilter }
    val today = java.time.LocalDate.now().toString()

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(spacing.xxl), contentAlignment = Alignment.Center) {
            Text(
                text = if (all.isEmpty()) "No tasks" else "No tasks with this priority",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        var lastKey = ""
        filtered.forEachIndexed { idx, task ->
            val key = isoDateKey(task.due_at ?: task.project_due_at)
            if (key != lastKey) {
                item(key = "div-$idx-$key") {
                    DayDivider(
                        label = formatRelativeDay(task.due_at ?: task.project_due_at),
                        highlight = key == today,
                    )
                }
                lastKey = key
            }
            item(key = "tk-${task.id}") {
                TaskRow(task = task, onDetail = { onTaskDetail(task.id) })
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
        (1..4).forEach { p -> FilterChip("≤$p", selected == p) { onSelect(p) } }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else GvColors.BgLight)
            .border(1.dp, if (active) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(16.dp))
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
        modifier = Modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = GvColors.BorderLight)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = spacing.md),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = GvColors.BorderLight)
    }
}

@Composable
private fun TaskRow(task: TaskByDueDateResponse, onDetail: () -> Unit) {
    val spacing = LocalSpacing.current
    val started = !task.started_at.isNullOrBlank()
    val overdue = isOverdue(task.due_at ?: task.project_due_at)
    val statusColor = taskTypeColor(task.task_type, started)
    val pColor = priorityColor(task.priority)
    val borderColor = if (overdue) GvColors.Danger.copy(alpha = 0.6f) else GvColors.BorderLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onDetail() },
    ) {
        // Priority left strip — urgent tasks read at a glance.
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(pColor))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = GvColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (task.blocked) {
                    Icon(Icons.Filled.Block, contentDescription = "Blocked", tint = GvColors.Danger, modifier = Modifier.size(16.dp))
                }
            }
            task.project_name?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                StatusBadge(statusLabel(task.started_at, task.task_type, task.recurrence), statusColor)
                task.due_at?.let { MetaPill(formatShortDate(it), GvColors.TextMuted) }
                if (task.time_spent > 0) MetaPill(formatDurationShort(task.time_spent), GvColors.TextMuted)
            }
        }
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
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
        Text("P$priority", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ---------- Projects tab ----------

@Composable
private fun ProjectsTab(nodes: List<ActiveTreeNode>, onTaskDetail: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    if (nodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(spacing.xxl), contentAlignment = Alignment.Center) {
            Text("No active projects", style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
        }
        return
    }
    // Projects start collapsed; the user expands the ones they want to drill into.
    val expanded = rememberSaveable(saver = collapsedSaver()) { mutableStateOf(emptySet<Int>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        val flat = flattenTree(nodes, expanded.value, depth = 0)
        items(flat, key = { "${it.node.type}-${it.node.id}-${it.depth}" }) { entry ->
            TreeRow(
                entry = entry,
                isCollapsed = entry.node.id !in expanded.value,
                onToggle = {
                    expanded.value = expanded.value.toMutableSet().apply {
                        if (!add(entry.node.id)) remove(entry.node.id)
                    }
                },
                onTaskDetail = onTaskDetail,
            )
        }
    }
}

private data class TreeEntry(val node: ActiveTreeNode, val depth: Int)

private fun flattenTree(nodes: List<ActiveTreeNode>, expanded: Set<Int>, depth: Int): List<TreeEntry> {
    val out = mutableListOf<TreeEntry>()
    for (n in nodes) {
        out.add(TreeEntry(n, depth))
        if (n.type == "project" && n.id in expanded && !n.children.isNullOrEmpty()) {
            out.addAll(flattenTree(n.children, expanded, depth + 1))
        }
    }
    return out
}

@Composable
private fun TreeRow(entry: TreeEntry, isCollapsed: Boolean, onToggle: () -> Unit, onTaskDetail: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    val n = entry.node
    val indent = (entry.depth * 16).dp
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
            .padding(start = indent)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .clickable(enabled = !isProject) { onTaskDetail(n.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tree-child left rail (style-guide parity).
        if (entry.depth > 0 && !isProject) {
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(GvColors.Primary))
        }
        Row(
            modifier = Modifier.padding(end = spacing.md).padding(vertical = spacing.sm),
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
                        StatusBadge(statusLabel(n.started_at, n.task_type, n.recurrence), statusColor)
                        n.priority?.let { PriorityBadge(it, priorityColor(it)) }
                        n.due_at?.let { MetaPill(formatShortDate(it), GvColors.TextMuted) }
                    }
                } else {
                    n.due_at?.let {
                        Text("Due ${formatShortDate(it)}", style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
                    }
                }
            }
            if (n.blocked == true) {
                Icon(Icons.Filled.Block, contentDescription = "Blocked", tint = GvColors.Danger, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private fun collapsedSaver(): androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<Set<Int>>, ArrayList<Int>> =
    androidx.compose.runtime.saveable.Saver(
        save = { ArrayList(it.value) },
        restore = { androidx.compose.runtime.mutableStateOf(it.toSet()) },
    )

// ---------- Shared ----------

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
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
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
    }
}

@Composable
private fun CenteredLoader() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GvColors.Primary)
    }
}
