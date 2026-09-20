package com.gv.app.ui.tasks

import com.gv.app.ui.common.FilterChip
import com.gv.app.ui.common.gvFieldColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.CreatePlanBlockRequest
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.UpdatePlanBlockRequest
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

/**
 * Wall-clock start / end of a plan block, resolved against the day it is anchored to. An end of
 * 00:00 means the end of that day, so it lands on the *next* midnight; without that, a block
 * running to midnight would end before it starts.
 */
internal fun blockInstants(day: LocalDate, start: LocalTime, end: LocalTime): Pair<LocalDateTime, LocalDateTime> {
    val endDay = if (end == LocalTime.MIDNIGHT) day.plusDays(1) else day
    return day.atTime(start) to endDay.atTime(end)
}

/** A tappable `HH:mm` that opens the clock dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(label: String, value: LocalTime, modifier: Modifier = Modifier, onChange: (LocalTime) -> Unit) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
        ) {
            Text(value.format(TimeLabel), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
        }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = GvColors.BgLight,
            title = { Text(label, color = GvColors.Text) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { onChange(LocalTime.of(state.hour, state.minute)); open = false }) {
                    Text("OK", color = GvColors.Primary)
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = GvColors.TextMuted) } },
        )
    }
}

/** A read-only field that opens the task picker, in the same shape as the other fields. */
@Composable
internal fun TaskField(label: String, taskName: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = taskName ?: "Select a task…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (taskName == null) GvColors.TextMuted else GvColors.Text,
                modifier = Modifier.weight(1f),
            )
            if (enabled) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GvColors.TextMuted)
        }
    }
}

/**
 * Creates or edits a plan block. A block is either a task's slot or free time (a label of your
 * own); when editing, it keeps its own day, otherwise it lands on the day being viewed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanBlockSheet(
    block: PlanBlockResponse?,
    date: LocalDate,
    plan: PlanViewModel,
    loadTasks: suspend () -> PickerTasks,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val anchor = remember(block, date) {
        block?.plan_date?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() } ?: date
    }

    var free by remember { mutableStateOf(block != null && block.task_id == null) }
    var taskId by remember { mutableStateOf(block?.task_id) }
    var taskName by remember { mutableStateOf(block?.task_name) }
    var label by remember { mutableStateOf(block?.label.orEmpty()) }
    var note by remember { mutableStateOf(block?.note.orEmpty()) }
    var start by remember {
        mutableStateOf(parseIso(block?.started_at)?.toLocalTime() ?: LocalTime.now().withMinute(0).withSecond(0).withNano(0))
    }
    var end by remember {
        mutableStateOf(parseIso(block?.ended_at)?.toLocalTime() ?: start.plusHours(1))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    fun save() {
        val (from, to) = blockInstants(anchor, start, end)
        error = when {
            !to.isAfter(from) -> "End time must be after start time"
            !free && taskId == null -> "Choose a task or switch to free time"
            free && label.isBlank() -> "Describe what you will do during free time"
            else -> null
        }
        if (error != null) return
        saving = true
        val done: (Boolean) -> Unit = { ok ->
            saving = false
            if (ok) onDismiss()
        }
        val startedAt = localDateTimeToIsoUtc(from)
        val endedAt = localDateTimeToIsoUtc(to)
        if (block == null) {
            plan.createBlock(
                CreatePlanBlockRequest(
                    started_at = startedAt,
                    ended_at = endedAt,
                    task_id = if (free) null else taskId,
                    label = label.trim().ifEmpty { null },
                    note = note.trim().ifEmpty { null },
                ),
                done,
            )
        } else {
            plan.updateBlock(
                block.id,
                UpdatePlanBlockRequest(
                    started_at = startedAt,
                    ended_at = endedAt,
                    task_id = if (free) null else taskId,
                    clear_task = free,
                    label = label.trim().ifEmpty { null },
                    note = note.trim().ifEmpty { null },
                    clear_note = note.isBlank(),
                ),
                done,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column {
                Text(if (block == null) "New block" else "Edit block", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
                Text(formatRelativeDate(anchor), style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FilterChip("Task", !free) { free = false }
                FilterChip("Free time", free) { free = true }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TimeField("From", start, Modifier.weight(1f)) { start = it }
                TimeField("Until", end, Modifier.weight(1f)) { end = it }
            }

            if (!free) {
                TaskField("Task", taskName) { picking = true }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(if (free) "What you will do" else "Label (optional)") },
                placeholder = { Text(if (free) "lunch, walk, gym…" else "Defaults to the task name", color = GvColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = gvFieldColors(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = gvFieldColors(),
            )

            error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = GvColors.Danger) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
                Button(
                    onClick = ::save,
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                    modifier = Modifier.weight(1f),
                ) { Text(if (block == null) "Create" else "Save") }
            }
            Box(Modifier.padding(bottom = spacing.lg))
        }
    }

    if (picking) {
        TaskPickerSheet(
            load = loadTasks,
            currentTaskId = taskId,
            onPick = {
                taskId = it.id
                taskName = it.name
                label = it.name
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
