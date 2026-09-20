package com.gv.app.ui.tasks

import com.gv.app.ui.common.FilterChip
import com.gv.app.ui.common.gvFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.CreateCommitmentRequest
import com.gv.app.domain.model.RecurringCommitmentResponse
import com.gv.app.domain.model.UpdateCommitmentRequest
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val HmFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

private fun parseTime(s: String, fallback: LocalTime): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(fallback)

/**
 * Recurring commitments: weekly blocks (work, gym…) that the API turns into plan blocks and
 * counts against free capacity. Read live each time it opens. The task of an existing
 * commitment cannot change — the API has no field for it — so editing shows it, and changing
 * it means deleting and recreating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommitmentsSheet(
    plan: PlanViewModel,
    loadTasks: suspend () -> PickerTasks,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var commitments by remember { mutableStateOf<List<RecurringCommitmentResponse>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { commitments = plan.commitments() ?: emptyList() }

    // null while creating; otherwise the commitment being edited.
    var editing by remember { mutableStateOf<RecurringCommitmentResponse?>(null) }
    var taskId by remember { mutableStateOf<Int?>(null) }
    var taskName by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var start by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(LocalTime.of(17, 0)) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    fun resetForm() {
        editing = null; taskId = null; taskName = null; label = ""; days = emptySet()
        start = LocalTime.of(9, 0); end = LocalTime.of(17, 0)
    }

    fun startEdit(c: RecurringCommitmentResponse) {
        editing = c; taskId = c.task_id; taskName = c.task_name; label = c.label
        days = c.days_of_week.toSet()
        start = parseTime(c.start_time, LocalTime.of(9, 0)); end = parseTime(c.end_time, LocalTime.of(17, 0))
    }

    val afterWrite: (Boolean) -> Unit = { ok ->
        saving = false
        if (ok) {
            resetForm()
            reload++
        }
    }

    fun save() {
        if (label.isBlank() || days.isEmpty()) return
        val current = editing
        if (current == null && taskId == null) return
        saving = true
        val sortedDays = days.sorted()
        if (current != null) {
            plan.updateCommitment(
                current.id,
                UpdateCommitmentRequest(
                    label = label.trim(),
                    days_of_week = sortedDays,
                    start_time = start.format(HmFormat),
                    end_time = end.format(HmFormat),
                ),
                afterWrite,
            )
        } else {
            plan.createCommitment(
                CreateCommitmentRequest(taskId!!, label.trim(), sortedDays, start.format(HmFormat), end.format(HmFormat)),
                afterWrite,
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
            Text("Recurring commitments", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)

            when (val list = commitments) {
                null -> Box(Modifier.fillMaxWidth().padding(spacing.lg)) {
                    CircularProgressIndicator(color = GvColors.Primary, modifier = Modifier.size(24.dp))
                }
                else -> if (list.isEmpty()) {
                    Text("None yet", style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
                } else {
                    list.forEach { c ->
                        CommitmentRow(
                            c,
                            onEdit = { startEdit(c) },
                            onToggle = { plan.updateCommitment(c.id, UpdateCommitmentRequest(active = !c.active)) { reload++ } },
                            onDelete = {
                                plan.deleteCommitment(c.id) { ok ->
                                    if (ok) {
                                        if (editing?.id == c.id) resetForm()
                                        reload++
                                    }
                                }
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = GvColors.BorderLight)

            if (editing == null) {
                TaskField("Task", taskName) { picking = true }
            } else {
                Text(
                    "Task: ${taskName ?: "—"} (can't be changed — delete and recreate instead)",
                    style = MaterialTheme.typography.labelMedium,
                    color = GvColors.TextMuted,
                )
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                placeholder = { Text("Work", color = GvColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = gvFieldColors(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Days", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    DayNames.forEachIndexed { i, name ->
                        FilterChip(name, i in days) { days = if (i in days) days - i else days + i }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TimeField("Starts", start, Modifier.weight(1f)) { start = it }
                TimeField("Ends", end, Modifier.weight(1f)) { end = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (editing != null) {
                    OutlinedButton(onClick = ::resetForm, enabled = !saving, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = GvColors.TextMuted)
                    }
                }
                Button(
                    onClick = ::save,
                    enabled = !saving && label.isNotBlank() && days.isNotEmpty() && (editing != null || taskId != null),
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                    modifier = Modifier.weight(1f),
                ) { Text(if (editing != null) "Save changes" else "Add commitment") }
            }
            Box(Modifier.padding(bottom = spacing.lg))
        }
    }

    if (picking) {
        TaskPickerSheet(
            load = loadTasks,
            currentTaskId = taskId,
            onPick = { taskId = it.id; taskName = it.name; if (label.isBlank()) label = it.name; picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CommitmentRow(c: RecurringCommitmentResponse, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val muted = if (c.active) GvColors.Text else GvColors.TextMuted
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.label, style = MaterialTheme.typography.bodyMedium, color = muted, textDecoration = if (c.active) null else TextDecoration.LineThrough)
            Text(
                "${c.task_name} · ${c.days_of_week.joinToString(" ") { DayNames.getOrElse(it) { "?" } }} · ${c.start_time}–${c.end_time}",
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.TextMuted,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                if (c.active) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (c.active) "Deactivate" else "Activate",
                tint = if (c.active) GvColors.Success else GvColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
