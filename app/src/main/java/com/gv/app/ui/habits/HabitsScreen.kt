package com.gv.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import com.gv.app.ui.common.SmallButton
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Virtual page window centred on the reference day so the user can swipe ~±270 years.
private const val PAGE_CENTER = 100_000
private const val PAGE_COUNT = 200_001

private fun pageToDate(reference: LocalDate, page: Int): LocalDate =
    reference.plusDays((page - PAGE_CENTER).toLong())

private fun dateToPage(reference: LocalDate, date: LocalDate): Int =
    PAGE_CENTER + ChronoUnit.DAYS.between(reference, date).toInt()

@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel()) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val pending by vm.pending.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<HabitEditor?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Stable reference day for the page↔date mapping (fixed for the screen's lifetime).
    val reference = remember { LocalDate.now() }
    val pagerState = rememberPagerState(
        initialPage = dateToPage(reference, date),
        pageCount = { PAGE_COUNT },
    )

    LaunchedEffect(vm) {
        vm.toast.collect { message -> snackbar.showSnackbar(message) }
    }

    // Swipe settles → move the selected day.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            vm.onDateChange(pageToDate(reference, page))
        }
    }
    // External day change (prev/next/today buttons) → animate the pager to match.
    LaunchedEffect(date) {
        val target = dateToPage(reference, date)
        if (target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DateHeader(
                date = date,
                onPrev = vm::onPrevDay,
                onNext = vm::onNextDay,
                onToday = vm::onToday,
                onPickDate = { showDatePicker = true },
                onNewHabit = { editor = HabitEditor.New },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { it },
            ) { page ->
                val pageDate = pageToDate(reference, page)
                HabitDayPage(
                    vm = vm,
                    date = pageDate,
                    isCurrent = pageDate == date,
                    refreshing = refreshing,
                    pending = pending,
                    onEdit = { editor = HabitEditor.Edit(it) },
                    onNewHabit = { editor = HabitEditor.New },
                )
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

    when (val target = editor) {
        null -> Unit
        else -> {
            val habit = (target as? HabitEditor.Edit)?.habit
            HabitFormSheet(
                habit = habit,
                onSave = { form, onDone -> vm.onSave(habit, form, onDone) },
                onDelete = habit?.let { h -> { vm.onDelete(h.id) } },
                onDismiss = { editor = null },
            )
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initial = date,
            onPick = { vm.onDateChange(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** What the habit form is open on: a blank new habit, or an existing one. */
private sealed interface HabitEditor {
    data object New : HabitEditor
    data class Edit(val habit: HabitWithLog) : HabitEditor
}

@Composable
private fun HabitDayPage(
    vm: HabitsViewModel,
    date: LocalDate,
    isCurrent: Boolean,
    refreshing: Boolean,
    pending: Map<HabitLogKey, Double>,
    onEdit: (HabitWithLog) -> Unit,
    onNewHabit: () -> Unit,
) {
    val flow = remember(date) { vm.habitsFor(date) }
    val habits by flow.collectAsStateWithLifecycle(initialValue = emptyList())

    when {
        habits.isNotEmpty() -> HabitsList(
            habits = habits,
            optimistic = { habit -> pending[HabitLogKey(habit.id, date)] },
            onAdjust = { habit, delta -> vm.onAdjust(habit, date, delta) },
            onSetValue = { habit, value -> vm.onSetValue(habit.id, date, value) },
            onEdit = onEdit,
        )
        // Only the visible day shows the spinner; adjacent pre-rendered pages stay quiet.
        isCurrent && refreshing -> CenteredLoader()
        else -> EmptyState(onNewHabit)
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
    onNewHabit: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val isToday = date == LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.BgLight)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous day",
                    tint = GvColors.Text,
                )
            }

            // Tapping the date opens a calendar, as on the web.
            Column(
                modifier = Modifier.clickable(onClick = onPickDate),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.titleMedium,
                    color = GvColors.Text,
                )
                if (isToday) {
                    Text(
                        text = "Today · swipe to change day",
                        style = MaterialTheme.typography.labelMedium,
                        color = GvColors.Primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    TextButton(onClick = onToday) {
                        Text("Jump to today", color = GvColors.Primary)
                    }
                }
            }

            IconButton(onClick = onNext) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next day",
                    tint = GvColors.Text,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = spacing.xs), horizontalArrangement = Arrangement.End) {
            SmallButton("New habit", onNewHabit, icon = Icons.Filled.Add)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // The picker speaks UTC midnights; reading it back in UTC keeps the day the user tapped.
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK", color = GvColors.Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = GvColors.TextMuted) } },
        colors = DatePickerDefaults.colors(containerColor = GvColors.BgLight),
    ) { DatePicker(state = state) }
}

@Composable
private fun HabitsList(
    habits: List<HabitWithLog>,
    optimistic: (HabitWithLog) -> Double?,
    onAdjust: (HabitWithLog, Double) -> Unit,
    onSetValue: (HabitWithLog, Double) -> Unit,
    onEdit: (HabitWithLog) -> Unit,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        items(items = habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                optimistic = optimistic(habit),
                onAdjust = { delta -> onAdjust(habit, delta) },
                onSetValue = { value -> onSetValue(habit, value) },
                onEdit = { onEdit(habit) },
            )
        }
    }
}

@Composable
private fun EmptyState(onNewHabit: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
    ) {
        Text(
            text = "No habits yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )
        SmallButton("Create one", onNewHabit, icon = Icons.Filled.Add)
    }
}

@Composable
private fun CenteredLoader() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = GvColors.Primary)
    }
}

private val DateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

private fun formatDate(date: LocalDate): String = date.format(DateFormatter)
