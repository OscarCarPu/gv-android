package com.gv.app.ui.calendar

import com.gv.app.ui.common.SmallButton
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate

/**
 * Calendar tab: events and plan blocks.
 *
 * Month shows a dot grid with the selected day's agenda beneath it; week and day share one time
 * grid. The source chips choose events, plan blocks or both (an event with a plan shows once).
 */
@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<CalendarSheet?>(null) }

    LaunchedEffect(vm) { vm.toast.collect { snackbar.showSnackbar(it) } }

    // Coming back to the tab re-reads the range, which covers whatever changed elsewhere.
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(GvColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            CalendarHeader(
                state = state,
                vm = vm,
                onOpenPanel = { sheet = CalendarSheet.Panel },
                onNewEvent = { sheet = CalendarSheet.Event(EventSheetTarget.New(state.anchor, null)) },
            )

            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = GvColors.Primary,
                    trackColor = Color.Transparent,
                )
            }
            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = GvColors.Danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GvColors.Danger.copy(alpha = 0.12f))
                        .padding(horizontal = LocalSpacing.current.xl, vertical = LocalSpacing.current.sm),
                )
            }

            Box(Modifier.weight(1f)) {
                when (state.mode) {
                    CalendarViewMode.MONTH -> MonthView(
                        state = state,
                        onSelectDay = vm::openDayInMonth,
                        onOpenDay = vm::openDay,
                        onItemClick = { item -> item.event?.let { sheet = CalendarSheet.Event(EventSheetTarget.Existing(it)) } },
                    )

                    CalendarViewMode.WEEK, CalendarViewMode.DAY -> TimeGridView(
                        state = state,
                        onItemClick = { item -> item.event?.let { sheet = CalendarSheet.Event(EventSheetTarget.Existing(it)) } },
                        onCreateAt = { day, hour ->
                            sheet = CalendarSheet.Event(EventSheetTarget.New(day, hour))
                        },
                        onOpenDay = vm::openDay,
                    )
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

    when (val open = sheet) {
        is CalendarSheet.Panel -> CalendarsSheet(freeBusy = state.freeBusy, onDismiss = { sheet = null })

        is CalendarSheet.Event -> EventSheet(
            target = open.target,
            vm = vm,
            hasPlan = (open.target as? EventSheetTarget.Existing)?.event?.let { state.hasPlan(it.id) } == true,
            onCreatePlan = { sheet = CalendarSheet.PlanFromEvent(it) },
            onDismiss = { sheet = null },
        )

        is CalendarSheet.PlanFromEvent -> PlanFromEventSheet(
            event = open.event,
            vm = vm,
            onDismiss = { sheet = null },
        )

        null -> Unit
    }
}

private sealed interface CalendarSheet {
    data object Panel : CalendarSheet
    data class Event(val target: EventSheetTarget) : CalendarSheet
    data class PlanFromEvent(val event: com.gv.app.domain.model.CalendarEvent) : CalendarSheet
}

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    vm: CalendarViewModel,
    onOpenPanel: () -> Unit,
    onNewEvent: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().background(GvColors.BgLight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.shift(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous",
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = GvColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { vm.shift(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            SmallButton("New", onNewEvent, icon = Icons.Filled.Add)
            IconButton(onClick = onOpenPanel) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "Free time",
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CalendarSource.entries.forEach { source ->
                ModeChip(
                    label = source.label,
                    active = source == state.source,
                    onClick = { vm.setSource(source) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarViewMode.entries.forEach { mode ->
                ModeChip(
                    label = mode.label,
                    active = mode == state.mode,
                    onClick = { vm.setMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
            ModeChip(
                label = "Today",
                active = false,
                enabled = !state.isCurrentPeriod || state.anchor != LocalDate.now(),
                onClick = vm::goToday,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (active) GvColors.Primary else GvColors.BorderLight,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                active -> GvColors.Primary
                enabled -> GvColors.TextMuted
                else -> GvColors.TextMuted.copy(alpha = 0.4f)
            },
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
