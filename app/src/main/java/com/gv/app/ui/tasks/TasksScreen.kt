package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/** The web's two tabs: Today (timer + Due Soon + plan) and Projects (the active tree). */
private enum class TasksTab(val label: String) {
    TODAY("Today"),
    PROJECTS("Projects"),
}

/**
 * The Tasks screen: a Today | Projects toggle, the timer pinned beneath it, and the two tabs
 * as swipeable pages. Everything the sheets and dialogs need is owned here; the lists and the
 * plan render from [TasksViewModel] and [PlanViewModel] and never fetch on their own.
 */
@Composable
fun TasksScreen(vm: TasksViewModel = viewModel(), planVm: PlanViewModel = viewModel()) {
    val spacing = LocalSpacing.current
    val state by vm.state.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val plan by planVm.state.collectAsStateWithLifecycle()
    val editingDetail by vm.editingDetail.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(TasksTab.TODAY) }
    var timerExpanded by rememberSaveable { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var createInProject by remember { mutableStateOf<Int?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showAgenda by remember { mutableStateOf(false) }
    var showCommitments by remember { mutableStateOf(false) }
    var blockEditor by remember { mutableStateOf<BlockEditorTarget?>(null) }
    var openEntryId by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }

    // Swipe left/right between the tabs, kept in step with the toggle.
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { TasksTab.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> tab = TasksTab.entries[page] }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal) pagerState.animateScrollToPage(tab.ordinal)
    }

    LaunchedEffect(vm) { vm.toast.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(planVm) { planVm.toast.collect { snackbar.showSnackbar(it) } }

    val rowActions = remember(vm) {
        TaskRowActions(
            onDetail = vm::openDetail,
            onStart = vm::startTask,
            onFinish = { vm.finishOrRenew(it.id, it.task_type, it.recurrence) },
            onStartTimer = vm::startOrAssignTimer,
            onAssignTimer = vm::startOrAssignTimer,
            onStopAndStart = vm::stopAndStartTimer,
        )
    }
    val planActions = remember(vm, planVm) {
        PlanActions(
            onToggleBlock = planVm::toggleBlock,
            onEditBlock = { blockEditor = BlockEditorTarget(it) },
            onDeleteBlock = planVm::deleteBlock,
            onStartTimer = vm::startOrAssignTimer,
            onAssignTimer = vm::startOrAssignTimer,
            onStopAndStart = vm::stopAndStartTimer,
            onOpenEntry = { id ->
                // A running entry belongs to the timer panel; only finished ones open the editor.
                val entry = (state as? TasksUiState.Loaded)?.data?.todayEntries?.firstOrNull { it.id == id }
                if (entry?.finished_at == null) timerExpanded = true else openEntryId = id
            },
        )
    }
    val treeActions = remember(vm) {
        TreeActions(
            onDetail = vm::openDetail,
            onNewTask = { projectId -> createInProject = projectId; showCreate = true },
            onToggle = vm::toggleTreeNode,
            onStartTimer = vm::startOrAssignTimer,
            onAssignTimer = vm::startOrAssignTimer,
            onStopAndStart = vm::stopAndStartTimer,
        )
    }

    val loaded = state as? TasksUiState.Loaded

    Box(modifier = Modifier.fillMaxSize().background(GvColors.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(start = spacing.xl, end = spacing.xl, top = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                TabToggle(selected = tab, onSelect = { tab = it })
                TimerPanel(
                    timer = timer,
                    summary = loaded?.data?.summary,
                    expanded = timerExpanded,
                    onToggleExpanded = { timerExpanded = !timerExpanded },
                    onPickTask = { showPicker = true },
                    onOpenTask = vm::openDetail,
                    onStop = vm::stopTimer,
                    onCancel = vm::cancelTimer,
                    onCommentChange = vm::updateTimerComment,
                    onEditStart = vm::editActiveTimerStart,
                    onAgenda = { showAgenda = true },
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loaded == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GvColors.Primary)
                    }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                        when (TasksTab.entries[page]) {
                            TasksTab.TODAY -> TodayTab(
                                tasks = loaded,
                                plan = plan,
                                timerRunning = timer.isRunning,
                                rowActions = rowActions,
                                planActions = planActions,
                                onDuePriority = vm::setDuePriority,
                                onDueProject = vm::setDueProject,
                                onShowMore = vm::showMoreDue,
                                onNewTask = { createInProject = null; showCreate = true },
                                onSelectPlanDate = planVm::selectDate,
                                onBackToToday = planVm::backToToday,
                                onNewBlock = { blockEditor = BlockEditorTarget(null) },
                                onClearFuture = planVm::clearFuture,
                                onCommitments = { showCommitments = true },
                            )
                            TasksTab.PROJECTS -> ProjectsTab(
                                nodes = loaded.tree,
                                priority = loaded.filters.treePriority,
                                timerRunning = timer.isRunning,
                                actions = treeActions,
                                onPriority = vm::setTreePriority,
                            )
                        }
                    }
                }

                SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
                    Snackbar(snackbarData = data, containerColor = GvColors.Surface, contentColor = GvColors.Text)
                }
            }
        }
    }

    // ----- Sheets and dialogs -----

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
            onTimerStart = { vm.startOrAssignTimer(task.id); vm.closeDetail() },
            onAddTodo = vm::addTodo,
            onToggleTodo = vm::toggleTodo,
            onDeleteTodo = vm::deleteTodo,
            timerRunning = timer.isRunning,
        )
    }

    if (showCreate) {
        TaskCreateSheet(
            projects = loaded?.data?.projects.orEmpty(),
            prefillProjectId = createInProject,
            onDismiss = { showCreate = false; createInProject = null },
            onCreate = { req, startNow ->
                vm.createTask(req, startNow) { ok -> if (ok) { showCreate = false; createInProject = null } }
            },
        )
    }

    if (showPicker) {
        TaskPickerSheet(
            load = vm::pickerTasks,
            currentTaskId = timer.active?.taskId,
            onPick = { task ->
                // Idle: starts the timer on it. Running: re-points the running entry at it.
                vm.startOrAssignTimer(task.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
            onOpenDetail = timer.active?.let { active -> { showPicker = false; vm.openDetail(active.taskId) } },
        )
    }

    if (showAgenda) AgendaSheet(vm = vm, onDismiss = { showAgenda = false })

    if (showCommitments) {
        CommitmentsSheet(plan = planVm, loadTasks = vm::pickerTasks, onDismiss = { showCommitments = false })
    }

    blockEditor?.let { target ->
        PlanBlockSheet(
            block = target.block,
            date = plan.date,
            plan = planVm,
            loadTasks = vm::pickerTasks,
            onDismiss = { blockEditor = null },
        )
    }

    openEntryId?.let { id ->
        val entry = loaded?.data?.todayEntries?.firstOrNull { it.id == id }
        if (entry == null) openEntryId = null else TimeEntrySheet(entry, vm, onDismiss = { openEntryId = null })
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

/** Wraps the block being edited so that "new" (null block) is distinguishable from "closed". */
private class BlockEditorTarget(val block: PlanBlockResponse?)

/** The web's Today | Projects switch, as two equal-width segments. */
@Composable
private fun TabToggle(selected: TasksTab, onSelect: (TasksTab) -> Unit) {
    val spacing = LocalSpacing.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        TasksTab.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, if (active) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onSelect(entry) }
                    .padding(vertical = 8.dp),
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
