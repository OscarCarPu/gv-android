package com.gv.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.ui.common.FilterChip
import com.gv.app.ui.common.gvFieldColors
import com.gv.app.ui.tasks.DateTimeField
import com.gv.app.ui.tasks.TaskField
import com.gv.app.ui.tasks.TaskPickerSheet
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.ZoneId

/**
 * "Create plan" from an event, as on the web: a plan block linked to the event, on no task, an
 * existing one, or a new one made here, at the event's times or different ones. If the times are
 * changed, the event is rescheduled to match — except an all-day event, which stays all-day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanFromEventSheet(event: CalendarEvent, vm: CalendarViewModel, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val zone = remember { ZoneId.systemDefault() }
    val defaults = remember(event) { defaultPlanTimes(event, zone) }

    var mode by remember { mutableStateOf(PlanTaskMode.NONE) }
    var taskId by remember { mutableStateOf<Int?>(null) }
    var taskName by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(defaults.first) }
    var end by remember { mutableStateOf(defaults.second) }
    var timeError by remember { mutableStateOf(false) }
    var taskError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    fun submit() {
        when (val check = checkPlanFromEvent(event, mode, taskId, newName, start, end, zone)) {
            is PlanFromEventCheck.Invalid -> {
                timeError = check.timeError
                taskError = check.taskError
            }
            is PlanFromEventCheck.Ok -> {
                saving = true
                failure = null
                vm.createPlanFromEvent(event, check) { error ->
                    saving = false
                    if (error == null) onDismiss() else failure = error
                }
            }
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
                Text("Create plan", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
                Text(event.summary.ifBlank { "(no title)" }, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Task", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilterChip("No task", mode == PlanTaskMode.NONE) { mode = PlanTaskMode.NONE; taskError = false }
                    FilterChip("Existing", mode == PlanTaskMode.EXISTING) { mode = PlanTaskMode.EXISTING; taskError = false }
                    FilterChip("New", mode == PlanTaskMode.NEW) { mode = PlanTaskMode.NEW; taskError = false }
                }
            }

            when (mode) {
                PlanTaskMode.NONE -> Unit
                PlanTaskMode.EXISTING -> TaskField("Choose a task", taskName) { picking = true }
                PlanTaskMode.NEW -> OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; taskError = false },
                    label = { Text("New task name") },
                    singleLine = true,
                    isError = taskError,
                    modifier = Modifier.fillMaxWidth(),
                    colors = gvFieldColors(),
                )
            }
            if (taskError && mode == PlanTaskMode.EXISTING) {
                Text("Pick a task, or switch to \"No task\"", style = MaterialTheme.typography.labelMedium, color = GvColors.Danger)
            }

            DateTimeField("Starts", start) { start = it; timeError = false }
            DateTimeField("Ends", end) { end = it; timeError = false }
            if (timeError) {
                Text("The end has to be after the start", style = MaterialTheme.typography.labelMedium, color = GvColors.Danger)
            }
            if (!event.all_day) {
                Text(
                    "Changing the times reschedules the event to match.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GvColors.TextMuted,
                )
            }

            failure?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = GvColors.Danger) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
                Button(
                    onClick = ::submit,
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                    modifier = Modifier.weight(1f),
                ) { Text(if (saving) "Creating…" else "Create plan") }
            }
            Box(Modifier.padding(bottom = spacing.lg))
        }
    }

    if (picking) {
        TaskPickerSheet(
            load = vm::pickerTasks,
            currentTaskId = taskId,
            onPick = { taskId = it.id; taskName = it.name; taskError = false; picking = false },
            onDismiss = { picking = false },
        )
    }
}
