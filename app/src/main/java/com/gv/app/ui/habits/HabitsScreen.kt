package com.gv.app.ui.habits

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
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
import java.util.Locale

@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<HabitWithLog?>(null) }

    LaunchedEffect(vm) {
        vm.toast.collect { message -> snackbar.showSnackbar(message) }
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
            )

            when (val s = state) {
                is HabitsUiState.Loading -> CenteredLoader()
                is HabitsUiState.Error -> ErrorState(message = s.message, onRetry = vm::refresh)
                is HabitsUiState.Loaded -> HabitsList(
                    habits = s.habits,
                    onAdjust = vm::onAdjust,
                    onSetValue = vm::onSetValue,
                    onLongPress = { pendingDelete = it },
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

    pendingDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = GvColors.BgLight,
            title = { Text("Delete habit?", color = GvColors.Text) },
            text = {
                Text(
                    "\"${habit.name}\" and its history will be removed. This cannot be undone.",
                    color = GvColors.TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.onDelete(habit.id)
                        pendingDelete = null
                    },
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

@Composable
private fun DateHeader(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val isToday = date == LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.BgLight)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatDate(date),
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
            )
            if (isToday) {
                Text(
                    text = "Today",
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
}

@Composable
private fun HabitsList(
    habits: List<HabitWithLog>,
    onAdjust: (Int, Double) -> Unit,
    onSetValue: (Int, Double) -> Unit,
    onLongPress: (HabitWithLog) -> Unit,
) {
    val spacing = LocalSpacing.current

    if (habits.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No habits yet. Create one from gv-web.",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        items(items = habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                onAdjust = { delta -> onAdjust(habit.id, delta) },
                onSetValue = { value -> onSetValue(habit.id, value) },
                onLongPress = { onLongPress(habit) },
            )
        }
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )
        OutlinedButton(onClick = onRetry) {
            Text("Retry", color = GvColors.Primary)
        }
    }
}

private val DateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

private fun formatDate(date: LocalDate): String = date.format(DateFormatter)
