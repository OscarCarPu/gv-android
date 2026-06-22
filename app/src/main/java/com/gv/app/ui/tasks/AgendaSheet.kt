package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.TaskOption
import com.gv.app.domain.model.TimeEntryWithTaskResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val entry: TimeEntryWithTaskResponse) : EditorTarget
}

private val DateTimeLabel = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.UK)
private val HourLabel = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

/** Seconds between two ISO-UTC instants (0 if unparseable / open). */
private fun durationSecs(startIso: String, endIso: String?): Long {
    val s = parseIso(startIso)
    val e = endIso?.let { parseIso(it) }
    return if (s != null && e != null) java.time.Duration.between(s, e).seconds.coerceAtLeast(0L) else 0L
}

/** Synthetic agenda row for an optimistic (not-yet-synced) entry. */
private fun agendaRow(
    tempId: Int,
    taskId: Int,
    option: TaskOption?,
    startIso: String,
    endIso: String?,
    comment: String?,
) = TimeEntryWithTaskResponse(
    id = tempId,
    task_id = taskId,
    task_name = option?.name ?: "Task",
    task_type = null,
    recurrence = null,
    priority = null,
    project_id = null,
    project_name = option?.projectName,
    started_at = startIso,
    finished_at = endIso,
    comment = comment,
    task_finished_at = null,
    time_spent = durationSecs(startIso, endIso),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaSheet(vm: TasksViewModel, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var day by remember { mutableStateOf(LocalDate.now()) }
    var entries by remember { mutableStateOf<List<TimeEntryWithTaskResponse>?>(null) }
    var taskOptions by remember { mutableStateOf<List<TaskOption>>(emptyList()) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var tempId by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) { vm.loadTaskOptions { taskOptions = it } }
    // Reload from the server only when the day changes (or the sheet reopens); in-day mutations
    // update the list optimistically since the write is queued and won't be on the server yet.
    LaunchedEffect(day) {
        entries = null
        vm.loadDayEntries(day) { entries = it ?: emptyList() }
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
                .heightIn(min = 360.dp, max = 720.dp)
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            when (val e = editor) {
                null -> AgendaList(
                    day = day,
                    entries = entries,
                    onPrevDay = { day = day.minusDays(1) },
                    onNextDay = { day = day.plusDays(1) },
                    onToday = { day = LocalDate.now() },
                    onAdd = { editor = EditorTarget.New },
                    // Optimistic temp rows (id < 0) aren't yet on the server, so not re-editable.
                    onEdit = { if (it.id >= 0) editor = EditorTarget.Existing(it) },
                )
                else -> EntryEditor(
                    target = e,
                    day = day,
                    taskOptions = taskOptions,
                    onBack = { editor = null },
                    onSave = { taskId, startIso, endIso, comment ->
                        val opt = taskOptions.firstOrNull { it.id == taskId }
                        when (e) {
                            is EditorTarget.New -> if (endIso != null) {
                                vm.createPastEntry(taskId, startIso, endIso, comment)
                                val tid = tempId.also { tempId -= 1 }
                                entries = ((entries ?: emptyList()) + agendaRow(tid, taskId, opt, startIso, endIso, comment))
                                    .sortedBy { it.started_at }
                            }
                            is EditorTarget.Existing -> {
                                vm.editEntry(e.entry.id, taskId, startIso, endIso, comment)
                                entries = entries?.map { row ->
                                    if (row.id == e.entry.id) {
                                        row.copy(
                                            task_id = taskId,
                                            task_name = opt?.name ?: row.task_name,
                                            project_name = opt?.projectName ?: row.project_name,
                                            started_at = startIso,
                                            finished_at = endIso,
                                            comment = comment,
                                            time_spent = durationSecs(startIso, endIso),
                                        )
                                    } else row
                                }?.sortedBy { it.started_at }
                            }
                        }
                        editor = null
                    },
                    onDelete = (e as? EditorTarget.Existing)?.let {
                        {
                            vm.deleteEntry(it.entry.id)
                            entries = entries?.filterNot { row -> row.id == it.entry.id }
                            editor = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AgendaList(
    day: LocalDate,
    entries: List<TimeEntryWithTaskResponse>?,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TimeEntryWithTaskResponse) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Agenda", style = MaterialTheme.typography.titleLarge, color = GvColors.Text, modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add entry", tint = GvColors.Primary) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevDay) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day", tint = GvColors.Text) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.UK)), style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
            if (day != LocalDate.now()) {
                TextButton(onClick = onToday) { Text("Today", color = GvColors.Primary) }
            }
        }
        IconButton(onClick = onNextDay) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day", tint = GvColors.Text) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        when {
            entries == null -> Box(modifier = Modifier.fillMaxWidth().padding(spacing.xxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GvColors.Primary)
            }
            entries.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(spacing.xxl), contentAlignment = Alignment.Center) {
                Text("No entries this day", style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
            }
            else -> entries.forEach { entry -> AgendaRow(entry, onClick = { onEdit(entry) }) }
        }
    }
}

@Composable
private fun AgendaRow(entry: TimeEntryWithTaskResponse, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val start = parseIso(entry.started_at)
    val end = parseIso(entry.finished_at)
    val timeText = buildString {
        append(start?.format(HourLabel) ?: "—")
        append(" – ")
        append(end?.format(HourLabel) ?: "ongoing")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.Bg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.task_name, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text, fontWeight = FontWeight.SemiBold)
            Text(timeText, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
        }
        if (entry.time_spent > 0) {
            Text(formatDurationShort(entry.time_spent), style = MaterialTheme.typography.labelMedium, color = GvColors.Primary)
        }
    }
}

@Composable
private fun EntryEditor(
    target: EditorTarget,
    day: LocalDate,
    taskOptions: List<TaskOption>,
    onBack: () -> Unit,
    onSave: (taskId: Int, startIso: String, endIso: String?, comment: String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val spacing = LocalSpacing.current
    val existing = (target as? EditorTarget.Existing)?.entry

    var taskId by remember { mutableStateOf(existing?.task_id) }
    var taskName by remember { mutableStateOf(existing?.task_name ?: "") }
    var start by remember {
        mutableStateOf(existing?.let { parseIso(it.started_at) } ?: day.atTime(LocalTime.now().withSecond(0).withNano(0)))
    }
    var end by remember {
        mutableStateOf(existing?.let { parseIso(it.finished_at) } ?: start.plusHours(1))
    }
    var comment by remember { mutableStateOf(existing?.comment.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GvColors.Text) }
        Text(if (existing == null) "New entry" else "Edit entry", style = MaterialTheme.typography.titleLarge, color = GvColors.Text)
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        TaskPicker(options = taskOptions, selectedName = taskName, onSelect = { taskId = it.id; taskName = it.name })

        DateTimeField("Start", start) { start = it }
        DateTimeField("End", end) { end = it }

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Comment") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GvColors.Text, unfocusedTextColor = GvColors.Text,
                focusedBorderColor = GvColors.Primary, unfocusedBorderColor = GvColors.BorderLight,
                focusedLabelColor = GvColors.Primary, unfocusedLabelColor = GvColors.TextMuted,
                cursorColor = GvColors.Primary,
            ),
        )

        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = GvColors.Danger) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (onDelete != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = GvColors.Danger, modifier = Modifier.size(16.dp))
                    Text(" Delete", color = GvColors.Danger)
                }
            }
            Button(
                onClick = {
                    val id = taskId
                    when {
                        id == null -> error = "Pick a task"
                        !end.isAfter(start) -> error = "End must be after start"
                        else -> onSave(
                            id,
                            localDateTimeToIsoUtc(start),
                            localDateTimeToIsoUtc(end),
                            comment.trim().ifEmpty { null },
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                modifier = Modifier.weight(if (onDelete != null) 1f else 2f),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun TaskPicker(options: List<TaskOption>, selectedName: String, onSelect: (TaskOption) -> Unit) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Task", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(GvColors.Bg)
                    .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = spacing.lg, vertical = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedName.ifEmpty { "Select task" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedName.isEmpty()) GvColors.TextMuted else GvColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GvColors.TextMuted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(opt.name, color = GvColors.Text)
                                opt.projectName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted) }
                            }
                        },
                        onClick = { onSelect(opt); open = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(label: String, value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    val spacing = LocalSpacing.current
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf(value.toLocalDate()) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .clickable { showDate = true }
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
        ) {
            Text(value.format(DateTimeLabel), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
        }
    }

    if (showDate) {
        val initMillis = value.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { pendingDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    showDate = false
                    showTime = true
                }) { Text("Next", color = GvColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel", color = GvColors.TextMuted) } },
            colors = DatePickerDefaults.colors(containerColor = GvColors.BgLight),
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = GvColors.BgLight,
            title = { Text("Time", color = GvColors.Text) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(pendingDate.atTime(timeState.hour, timeState.minute))
                    showTime = false
                }) { Text("OK", color = GvColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel", color = GvColors.TextMuted) } },
        )
    }
}
