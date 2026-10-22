package com.gv.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.UpdateEventRequest
import com.gv.app.ui.common.gvFieldColors
import com.gv.app.ui.tasks.DateTimeField
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What the sheet is for: a new event on a day (and maybe an hour), or an existing one. */
sealed interface EventSheetTarget {
    data class New(val day: LocalDate, val hour: Int?) : EventSheetTarget
    data class Existing(val event: CalendarEvent) : EventSheetTarget
}

/**
 * Create/edit sheet for one event: a name and a time range. A timed event is an instant, so the
 * wall clock the pickers collect is converted with the phone's offset — not stamped as if it were
 * UTC, which is what a task's `due_at` wants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    target: EventSheetTarget,
    vm: CalendarViewModel,
    /** Whether this event already has a plan block linked to it. */
    hasPlan: Boolean,
    onCreatePlan: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val event = (target as? EventSheetTarget.Existing)?.event
    val zone = remember { ZoneId.systemDefault() }
    val seed = remember(target) { seedTimes(target, zone) }

    var name by remember(target) { mutableStateOf(event?.name ?: "") }
    var start by remember(target) { mutableStateOf(seed.first) }
    var end by remember(target) { mutableStateOf(seed.second) }
    var nameError by remember(target) { mutableStateOf(false) }
    var timeError by remember(target) { mutableStateOf(false) }
    var confirmDelete by remember(target) { mutableStateOf(false) }
    /** Guards against a second tap creating a second event while the first is in flight. */
    var submitting by remember(target) { mutableStateOf(false) }

    fun save() {
        nameError = name.isBlank()
        timeError = !end.isAfter(start)
        if (nameError || timeError || submitting) return
        submitting = true
        val startIso = localToApiInstant(start, zone)
        val endIso = localToApiInstant(end, zone)
        val done = { ok: Boolean ->
            submitting = false
            if (ok) onDismiss()
        }
        if (event == null) {
            vm.createEvent(CreateEventRequest(name.trim(), startIso, endIso), done)
        } else {
            vm.updateEvent(event.id, UpdateEventRequest(name.trim(), startIso, endIso), done)
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                if (event == null) "New event" else "Edit event",
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Name") },
                singleLine = true,
                isError = nameError,
                modifier = Modifier.fillMaxWidth(),
                colors = gvFieldColors(),
            )

            DateTimeField("Starts", start) { start = it; timeError = false }
            DateTimeField("Ends", end) { end = it; timeError = false }
            if (timeError) {
                Text("The end has to be after the start", style = MaterialTheme.typography.labelMedium, color = GvColors.Danger)
            }

            if (event != null && !hasPlan) {
                OutlinedButton(onClick = { onCreatePlan(event) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Create plan", color = GvColors.Primary)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (event != null) {
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !submitting, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = GvColors.Danger)
                    }
                }
                Button(
                    onClick = ::save,
                    enabled = !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                    modifier = Modifier.weight(1f),
                ) { Text(if (submitting) "Saving…" else if (event == null) "Create" else "Save") }
            }
            Box(Modifier.padding(bottom = spacing.lg))
        }
    }

    if (confirmDelete && event != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete event?") },
            text = { Text(event.name.ifBlank { "(no title)" }) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    submitting = true
                    vm.deleteEvent(event.id) { ok ->
                        submitting = false
                        if (ok) onDismiss()
                    }
                }) { Text("Delete", color = GvColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = GvColors.BgLight,
        )
    }
}

/** The times the form opens on: the event's own, or an hour starting at the tapped slot. */
internal fun seedTimes(target: EventSheetTarget, zone: ZoneId): Pair<LocalDateTime, LocalDateTime> =
    when (target) {
        is EventSheetTarget.Existing -> {
            val start = apiInstantToLocal(target.event.start_time, zone) ?: LocalDateTime.now(zone)
            start to (apiInstantToLocal(target.event.end_time, zone) ?: start.plusHours(1))
        }

        is EventSheetTarget.New -> {
            val hour = target.hour ?: if (target.day == LocalDate.now(zone)) {
                (LocalTime.now(zone).hour + 1).coerceAtMost(23)
            } else {
                9
            }
            val start = target.day.atTime(hour, 0)
            start to start.plusHours(1)
        }
    }
