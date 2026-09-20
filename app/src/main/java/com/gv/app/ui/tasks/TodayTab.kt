package com.gv.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate

/**
 * Today, as on the web: Due Soon on top, Today's Plan below it, one page. Both are emitted into
 * a single list so the whole tab scrolls together.
 */
@Composable
internal fun TodayTab(
    tasks: TasksUiState.Loaded,
    plan: PlanUiState,
    timerRunning: Boolean,
    rowActions: TaskRowActions,
    planActions: PlanActions,
    onDuePriority: (Int?) -> Unit,
    onDueProject: (Int?) -> Unit,
    onShowMore: () -> Unit,
    onNewTask: () -> Unit,
    onSelectPlanDate: (LocalDate) -> Unit,
    onBackToToday: () -> Unit,
    onNewBlock: () -> Unit,
    onClearFuture: () -> Unit,
    onCommitments: () -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        dueSoonSection(
            view = tasks.due,
            filters = tasks.filters,
            timerRunning = timerRunning,
            actions = rowActions,
            onDuePriority = onDuePriority,
            onDueProject = onDueProject,
            onShowMore = onShowMore,
            onNewTask = onNewTask,
        )
        planSection(
            state = plan,
            timerRunning = timerRunning,
            actions = planActions,
            onSelectDate = onSelectPlanDate,
            onBackToToday = onBackToToday,
            onNewBlock = onNewBlock,
            onClearFuture = onClearFuture,
            onCommitments = onCommitments,
        )
    }
}
