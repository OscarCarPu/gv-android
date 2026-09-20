package com.gv.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.ui.common.FilterChip
import com.gv.app.ui.common.gvFieldColors
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/**
 * Create or edit a habit, as on the web: name, description, frequency, a minimum and a maximum
 * target, and whether a missing day breaks the streak. Editing also offers Delete — the web has
 * no habit delete in its UI, but Android always had one, and the edit sheet is a deliberate place
 * for it, unlike a long-press. It keeps its confirmation: it takes the habit's whole history
 * with it, and nothing here can bring that back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFormSheet(
    habit: HabitWithLog?,
    onSave: (HabitFormCheck.Ok, onDone: (Boolean) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current

    var name by remember { mutableStateOf(habit?.name.orEmpty()) }
    var description by remember { mutableStateOf(habit?.description.orEmpty()) }
    var frequency by remember { mutableStateOf(habit?.frequency ?: "daily") }
    var minText by remember { mutableStateOf(habit?.target_min?.let(::formatHabitValue).orEmpty()) }
    var maxText by remember { mutableStateOf(habit?.target_max?.let(::formatHabitValue).orEmpty()) }
    var recordingRequired by remember { mutableStateOf(habit?.recording_required ?: true) }
    var nameError by remember { mutableStateOf(false) }
    var targetError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        when (val check = checkHabitForm(name, description, frequency, minText, maxText, recordingRequired)) {
            is HabitFormCheck.Invalid -> {
                nameError = check.nameError
                targetError = check.targetError
            }
            is HabitFormCheck.Ok -> {
                saving = true
                onSave(check) { ok ->
                    saving = false
                    if (ok) onDismiss()
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
            Text(
                if (habit == null) "New habit" else "Edit habit",
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= HABIT_NAME_MAX) name = it
                    nameError = false
                },
                label = { Text("Name") },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) ({ Text("A name is required") }) else null,
                modifier = Modifier.fillMaxWidth(),
                colors = gvFieldColors(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = gvFieldColors(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Frequency", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    HabitFrequencies.forEach { f ->
                        FilterChip(f.replaceFirstChar { it.uppercase() }, frequency == f) { frequency = f }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TargetField("Min target", minText, targetError, Modifier.weight(1f)) {
                    minText = it
                    targetError = false
                }
                TargetField("Max target", maxText, targetError, Modifier.weight(1f)) {
                    maxText = it
                    targetError = false
                }
            }
            if (targetError) {
                Text(
                    "Targets must be numbers, not negative, and the minimum can't exceed the maximum",
                    style = MaterialTheme.typography.labelMedium,
                    color = GvColors.Danger,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Recording required", style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
                    Text(
                        if (recordingRequired) "A missing day breaks the streak" else "A missing day carries the last value forward",
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.TextMuted,
                    )
                }
                Switch(
                    checked = recordingRequired,
                    onCheckedChange = { recordingRequired = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = GvColors.Primary,
                        checkedThumbColor = GvColors.Text,
                    ),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
                Button(
                    onClick = ::save,
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                    modifier = Modifier.weight(1f),
                ) { Text(if (habit == null) "Create" else "Save") }
            }

            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete habit", color = GvColors.Danger)
                }
            }
            Column(Modifier.padding(bottom = spacing.lg)) {}
        }
    }

    if (confirmDelete && habit != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = GvColors.BgLight,
            title = { Text("Delete habit?", color = GvColors.Text) },
            text = {
                Text(
                    "\"${habit.name}\" and its whole history will be removed. This cannot be undone.",
                    color = GvColors.TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Danger, contentColor = GvColors.Text),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = GvColors.TextMuted) }
            },
        )
    }
}

@Composable
private fun TargetField(label: String, value: String, isError: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = gvFieldColors(),
    )
}
