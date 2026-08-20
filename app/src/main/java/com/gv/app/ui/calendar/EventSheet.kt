package com.gv.app.ui.calendar

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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateEventRequest
import com.gv.app.domain.model.EventScope
import com.gv.app.domain.model.GoogleCalendar
import com.gv.app.domain.model.SendUpdates
import com.gv.app.domain.model.UpdateEventRequest
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateLabel = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.UK)
private val TimeLabel = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

/** What the sheet is for: a new event on a day (and maybe an hour), or an existing one. */
sealed interface EventSheetTarget {
    data class New(val day: LocalDate, val hour: Int?) : EventSheetTarget
    data class Existing(val event: CalendarEvent) : EventSheetTarget
}

/**
 * Create/edit sheet for one event or occurrence.
 *
 * Three things here are easy to get wrong and expensive when they are:
 *
 * - **An all-day event's end is exclusive** in Google and in the API, while a person thinks in
 *   last-day-covered. The form collects the last day and converts on the way in and out, so a
 *   one-day event is `starts_at = D`, `ends_at = D+1`.
 * - **A timed event is an instant**, so the wall clock the pickers collect is converted with the
 *   phone's offset — not stamped as if it were UTC, which is what a task's `due_at` wants.
 * - **A rule that bounds itself is left alone.** Only a plain `FREQ=…` maps onto a preset;
 *   anything with `COUNT`, `UNTIL` or `INTERVAL` shows as "custom" and is sent back untouched,
 *   because offering it as "Weekly" would silently drop the clause that makes it specific.
 *
 * Changing which calendar an existing event lives on is deliberately *not* part of Save: that is
 * a move, it is a different endpoint, and across accounts it recreates the event under a new id.
 * It gets its own row, and it closes the sheet, because the reference this sheet is holding may
 * no longer exist afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    target: EventSheetTarget,
    calendars: List<GoogleCalendar>,
    defaultCalendarId: Int?,
    vm: CalendarViewModel,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val event = (target as? EventSheetTarget.Existing)?.event
    val writable = calendars.filter { it.writable && !it.deleted && it.sync_enabled }
    val readOnly = event != null && !event.editable

    val zone = remember { ZoneId.systemDefault() }
    val seed = remember(target) { seedForm(target, defaultCalendarId, zone) }

    var calendarId by remember(target) { mutableStateOf(seed.calendarId) }
    var summary by remember(target) { mutableStateOf(seed.summary) }
    var description by remember(target) { mutableStateOf(seed.description) }
    var location by remember(target) { mutableStateOf(seed.location) }
    var allDay by remember(target) { mutableStateOf(seed.allDay) }
    var startDate by remember(target) { mutableStateOf(seed.startDate) }
    var startTime by remember(target) { mutableStateOf(seed.startTime) }
    /** All-day: the **last day covered**, not the API's exclusive end. */
    var endDate by remember(target) { mutableStateOf(seed.endDate) }
    var endTime by remember(target) { mutableStateOf(seed.endTime) }
    var preset by remember(target) { mutableStateOf(seed.preset) }
    var scope by remember(target) { mutableStateOf(seed.scope) }
    var sendUpdates by remember(target) { mutableStateOf(SendUpdates.NONE) }
    var summaryError by remember(target) { mutableStateOf(false) }
    var calendarError by remember(target) { mutableStateOf(false) }
    var timeError by remember(target) { mutableStateOf(false) }
    var confirmDelete by remember(target) { mutableStateOf(false) }
    var movePickerOpen by remember(target) { mutableStateOf(false) }
    /** Guards against a second tap creating a second event while the first is in flight. */
    var submitting by remember(target) { mutableStateOf(false) }

    /** Only an occurrence of a series can be scoped; everything else edits the one event. */
    val canScope = event?.recurring == true && event.original_starts_at != null
    /**
     * Google can only move whole events, so an occurrence reference is refused with a `400`.
     * Read off the reference itself rather than inferred, because that is what would be sent.
     */
    val canMove = event != null && !event.instance_id.contains('@')
    /** The rule belongs to the series, so it can only be changed when editing all of it. */
    val canEditRecurrence = event == null || !canScope || scope == EventScope.ALL
    val hasAttendees = (event?.attendees?.size ?: 0) > 0

    fun validate(): Boolean {
        summaryError = summary.isBlank()
        calendarError = calendarId == null
        timeError = if (allDay) endDate.isBefore(startDate) else {
            !LocalDateTime.of(endDate, endTime).isAfter(LocalDateTime.of(startDate, startTime))
        }
        return !summaryError && !timeError && calendarId != null
    }

    fun recurrenceForRequest(): List<String>? = when {
        !canEditRecurrence -> null
        preset == RecurrencePreset.CUSTOM -> seed.originalRecurrence
        preset == RecurrencePreset.NONE -> emptyList()
        else -> presetToRule(preset, startDate)
    }

    fun apiStart(): String =
        if (allDay) startDate.toString() else localToApiInstant(LocalDateTime.of(startDate, startTime), zone)

    // The API takes an exclusive end for all-day events; the form collects the last day covered.
    fun apiEnd(): String =
        if (allDay) endDate.plusDays(1).toString() else localToApiInstant(LocalDateTime.of(endDate, endTime), zone)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl)
                .padding(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = when {
                    readOnly -> "Event"
                    event != null -> "Edit event"
                    else -> "New event"
                },
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
            )

            if (readOnly) {
                ReadOnlyNotice(event)
                EventFacts(event, zone)
            } else {
                CalendarField(
                    label = "Calendar",
                    calendars = if (event == null) writable else calendars,
                    selectedId = calendarId,
                    // An existing event's calendar is moved, not edited: see the sheet's doc.
                    enabled = event == null,
                    onSelect = { calendarId = it; calendarError = false },
                )
                if (calendarError) {
                    Text(
                        text = if (writable.isEmpty()) {
                            "Nothing to create this on yet — connect a Google account, or turn a " +
                                "calendar's sync on under the sliders in the header."
                        } else {
                            "Pick a calendar"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.Danger,
                    )
                }

                GvField(
                    label = "Title",
                    value = summary,
                    onValueChange = { summary = it; summaryError = false },
                    error = summaryError,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("All day", style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
                    Switch(
                        checked = allDay,
                        onCheckedChange = { next ->
                            // Switching rewrites both edges into the other shape, so the sheet
                            // never holds a half-converted pair.
                            if (next) {
                                endDate = maxOf(endDate, startDate)
                            } else {
                                startTime = LocalTime.of(9, 0)
                                endTime = LocalTime.of(10, 0)
                                endDate = startDate
                            }
                            allDay = next
                            timeError = false
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GvColors.Primary,
                            checkedTrackColor = GvColors.Primary.copy(alpha = 0.4f),
                            uncheckedThumbColor = GvColors.TextMuted,
                            uncheckedTrackColor = GvColors.Surface,
                        ),
                    )
                }

                DateTimeRow(
                    label = "Starts",
                    date = startDate,
                    time = startTime,
                    allDay = allDay,
                    onDate = { picked ->
                        // Dragging the start drags the end with it, keeping the length — what a
                        // calendar UI is expected to do.
                        val shift = java.time.temporal.ChronoUnit.DAYS.between(startDate, picked)
                        startDate = picked
                        endDate = endDate.plusDays(shift)
                        timeError = false
                    },
                    onTime = { startTime = it; timeError = false },
                )
                DateTimeRow(
                    label = if (allDay) "Last day" else "Ends",
                    date = endDate,
                    time = endTime,
                    allDay = allDay,
                    onDate = { endDate = it; timeError = false },
                    onTime = { endTime = it; timeError = false },
                )
                if (timeError) {
                    Text(
                        text = if (allDay) "The last day cannot be before the first" else "The end must be after the start",
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.Danger,
                    )
                }

                if (canScope) {
                    ScopeSelector(scope = scope, onChange = { scope = it })
                }

                EnumField(
                    label = "Repeats",
                    options = RecurrencePreset.entries.filter {
                        // "Custom" is not something to pick; it only describes what is there.
                        it != RecurrencePreset.CUSTOM || preset == RecurrencePreset.CUSTOM
                    }.map { it to it.label },
                    selectedLabel = preset.label,
                    enabled = canEditRecurrence,
                    disabledHint = "The rule belongs to the series — choose \"All events\" to change it",
                    onSelect = { preset = it },
                )

                GvField(label = "Location", value = location, onValueChange = { location = it })
                GvField(
                    label = "Description",
                    value = description,
                    onValueChange = { description = it },
                    singleLine = false,
                )

                if (hasAttendees) {
                    EnumField(
                        label = "Notify guests",
                        options = listOf(
                            SendUpdates.NONE to "Don't notify",
                            SendUpdates.EXTERNAL_ONLY to "Guests outside the organisation",
                            SendUpdates.ALL to "All guests",
                        ),
                        selectedLabel = when (sendUpdates) {
                            SendUpdates.ALL -> "All guests"
                            SendUpdates.EXTERNAL_ONLY -> "Guests outside the organisation"
                            else -> "Don't notify"
                        },
                        onSelect = { sendUpdates = it },
                    )
                }

                if (event != null) EventFacts(event, zone)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Button(
                        enabled = !submitting,
                        onClick = {
                            if (!validate()) return@Button
                            val calId = calendarId ?: return@Button
                            submitting = true
                            if (event == null) {
                                vm.createEvent(
                                    CreateEventRequest(
                                        calendar_id = calId,
                                        summary = summary.trim(),
                                        description = description,
                                        location = location,
                                        all_day = allDay,
                                        starts_at = apiStart(),
                                        ends_at = apiEnd(),
                                        recurrence = recurrenceForRequest()?.takeIf { it.isNotEmpty() },
                                        send_updates = sendUpdates,
                                    ),
                                ) { ok -> submitting = false; if (ok) onDismiss() }
                            } else {
                                vm.updateEvent(
                                    event.instance_id,
                                    UpdateEventRequest(
                                        summary = summary.trim(),
                                        description = description,
                                        location = location,
                                        all_day = allDay,
                                        starts_at = apiStart(),
                                        ends_at = apiEnd(),
                                        recurrence = recurrenceForRequest(),
                                        scope = if (canScope) scope else null,
                                        send_updates = sendUpdates,
                                    ),
                                ) { ok -> submitting = false; if (ok) onDismiss() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GvColors.Primary,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(if (event == null) "Create" else "Save")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = GvColors.TextMuted)
                    }
                }

                if (event != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canMove) {
                            TextButton(onClick = { movePickerOpen = true }) {
                                Text("Move to another calendar", color = GvColors.Primary)
                            }
                        }
                        TextButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = GvColors.Danger,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(" Delete", color = GvColors.Danger)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && event != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = GvColors.BgLight,
            title = { Text("Delete event?", color = GvColors.Text) },
            text = {
                Text(
                    text = if (canScope) {
                        when (scope) {
                            EventScope.ALL -> "The whole series will be removed."
                            EventScope.FOLLOWING -> "This occurrence and every later one will be removed."
                            else -> "Only this occurrence will be removed; the series keeps the rest."
                        }
                    } else {
                        "\"${event.summary.ifBlank { "(no title)" }}\" will be removed from Google."
                    },
                    color = GvColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteEvent(
                        event.instance_id,
                        if (canScope) scope else null,
                        sendUpdates,
                    ) { ok -> if (ok) onDismiss() }
                }) { Text("Delete", color = GvColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
            },
        )
    }

    if (movePickerOpen && event != null) {
        CalendarPickerSheet(
            title = "Move to",
            calendars = writable.filter { it.id != event.calendar_id },
            onSelect = { picked ->
                movePickerOpen = false
                vm.moveEvent(event.instance_id, picked.id, sendUpdates) { ok -> if (ok) onDismiss() }
            },
            onDismiss = { movePickerOpen = false },
        )
    }
}

// --- seeding -------------------------------------------------------------------------------

private data class FormSeed(
    val calendarId: Int?,
    val summary: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val preset: RecurrencePreset,
    val scope: String,
    /** Kept verbatim so a hand-written rule survives an edit that does not touch it. */
    val originalRecurrence: List<String>,
)

private fun seedForm(target: EventSheetTarget, defaultCalendarId: Int?, zone: ZoneId): FormSeed =
    when (target) {
        is EventSheetTarget.New -> {
            // The caller decides the hour: an hour slot in the time grid carries the one that was
            // tapped, and everything else starts on the next hour.
            val start = target.hour?.let { LocalTime.of(it, 0) }
                ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            FormSeed(
                calendarId = defaultCalendarId,
                summary = "",
                description = "",
                location = "",
                allDay = false,
                startDate = target.day,
                startTime = start,
                endDate = target.day,
                endTime = start.plusHours(1),
                preset = RecurrencePreset.NONE,
                scope = EventScope.ALL,
                originalRecurrence = emptyList(),
            )
        }

        is EventSheetTarget.Existing -> {
            val e = target.event
            // All-day edges come from the API's dates, not from converting its instants: those
            // are midnight in the *calendar's* zone, so converting them here shifts the day.
            val firstDay = e.start_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: apiInstantToLocal(e.starts_at, zone)?.toLocalDate()
                ?: LocalDate.now()
            val lastDay = e.end_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.minusDays(1)
                ?: apiInstantToLocal(e.ends_at, zone)?.toLocalDate()
                ?: firstDay
            val startLocal = apiInstantToLocal(e.starts_at, zone) ?: LocalDateTime.of(firstDay, LocalTime.of(9, 0))
            val endLocal = apiInstantToLocal(e.ends_at, zone) ?: startLocal.plusHours(1)
            FormSeed(
                calendarId = e.calendar_id,
                summary = e.summary,
                description = e.description,
                location = e.location,
                allDay = e.all_day,
                startDate = if (e.all_day) firstDay else startLocal.toLocalDate(),
                startTime = startLocal.toLocalTime(),
                endDate = if (e.all_day) maxOf(lastDay, firstDay) else endLocal.toLocalDate(),
                endTime = endLocal.toLocalTime(),
                preset = ruleToPreset(e.recurrence),
                scope = if (e.recurring) EventScope.INSTANCE else EventScope.ALL,
                originalRecurrence = e.recurrence.orEmpty(),
            )
        }
    }

// --- pieces --------------------------------------------------------------------------------

@Composable
private fun ReadOnlyNotice(event: CalendarEvent) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.Warning.copy(alpha = 0.14f))
            .padding(spacing.lg),
    ) {
        Text(
            // The kinds Google generates itself have no editable original, so the sheet opens
            // read-only instead of letting a write fail at the API.
            text = when (event.event_type) {
                "birthday" -> "Google keeps birthdays in step with your contacts, so this one cannot be edited here."
                "fromGmail" -> "Google created this from an email, so it cannot be edited here."
                "workingLocation" -> "Google manages working locations, so this cannot be edited here."
                else -> "This calendar is read-only, so the event cannot be changed."
            },
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.Text,
        )
    }
    Text(
        text = event.summary.ifBlank { "(no title)" },
        style = MaterialTheme.typography.titleSmall,
        color = GvColors.Text,
    )
    if (event.description.isNotBlank()) {
        Text(event.description, style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
    }
}

/** The facts a person checks and cannot change: when, where, whose calendar, who is coming. */
@Composable
private fun EventFacts(event: CalendarEvent, zone: ZoneId) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Fact("When", eventWhenLabel(event, zone))
        if (event.location.isNotBlank()) Fact("Where", event.location)
        Fact("Calendar", "${event.calendar_name} · ${event.account_email}")
        event.organizer_email?.takeIf { it.isNotBlank() }?.let { Fact("Organiser", it) }
        event.attendees?.takeIf { it.isNotEmpty() }?.let { list ->
            Fact("Guests", list.joinToString(", ") { it.display_name?.takeIf(String::isNotBlank) ?: it.email })
        }
        if (event.recurring) {
            Fact("Repeats", event.recurrence?.firstOrNull()?.removePrefix("RRULE:") ?: "yes")
        }
        event.html_link?.takeIf { it.isNotBlank() }?.let { link ->
            val context = LocalContext.current
            Row(
                modifier = Modifier.clickable { openInBrowser(context, link) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Open in Google Calendar",
                    style = MaterialTheme.typography.labelSmall,
                    color = GvColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val spacing = LocalSpacing.current
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GvColors.TextMuted,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = GvColors.Text)
    }
}

/**
 * What a change to a recurring series touches. Shown only for an occurrence of one, because the
 * API refuses `instance` / `following` without an occurrence reference rather than guessing.
 */
@Composable
private fun ScopeSelector(scope: String, onChange: (String) -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("This change applies to", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            listOf(
                EventScope.INSTANCE to "This event",
                EventScope.FOLLOWING to "This and later",
                EventScope.ALL to "All events",
            ).forEach { (value, label) ->
                val active = value == scope
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (active) GvColors.Primary else GvColors.BorderLight,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onChange(value) }
                        .padding(vertical = spacing.md, horizontal = spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) GvColors.Primary else GvColors.TextMuted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
