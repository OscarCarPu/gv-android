package com.gv.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.GoogleCalendar
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The form controls the calendar sheets are built from.
 *
 * Deliberately local to this package rather than shared with `ui/money` or `ui/tasks`: those have
 * their own copies, and a calendar needs a date-only field, a separate time field and a picker
 * that shows a calendar's colour, none of which the others want.
 */

private val FieldDateLabel = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.UK)
private val FieldTimeLabel = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

@Composable
internal fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    allDay: Boolean,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(Modifier.weight(2f)) { DateButton(date = date, onChange = onDate) }
            if (!allDay) {
                Box(Modifier.weight(1f)) { TimeButton(time = time, onChange = onTime) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateButton(date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBox(onClick = { open = true }) {
        Text(date.format(FieldDateLabel), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
    }
    if (open) {
        val zone = ZoneId.systemDefault()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(zone).toLocalDate())
                    }
                    open = false
                }) { Text("OK", color = GvColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel", color = GvColors.TextMuted) }
            },
            colors = DatePickerDefaults.colors(containerColor = GvColors.BgLight),
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeButton(time: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBox(onClick = { open = true }) {
        Text(time.format(FieldTimeLabel), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = GvColors.BgLight,
            title = { Text("Time", color = GvColors.Text) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK", color = GvColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel", color = GvColors.TextMuted) }
            },
        )
    }
}

@Composable
internal fun FieldBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.Bg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
internal fun GvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = GvColors.Text,
            unfocusedTextColor = GvColors.Text,
            focusedBorderColor = GvColors.Primary,
            unfocusedBorderColor = GvColors.BorderLight,
            errorBorderColor = GvColors.Danger,
            focusedLabelColor = GvColors.Primary,
            unfocusedLabelColor = GvColors.TextMuted,
            errorLabelColor = GvColors.Danger,
            cursorColor = GvColors.Primary,
        ),
    )
}

/** Dropdown over a fixed set of options, opened as a sheet like the rest of the app's pickers. */
@Composable
internal fun <T> EnumField(
    label: String,
    options: List<Pair<T, String>>,
    selectedLabel: String,
    enabled: Boolean = true,
    disabledHint: String? = null,
    onSelect: (T) -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.clickable { open = true } else Modifier)
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) GvColors.Text else GvColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GvColors.TextMuted)
        }
        if (!enabled && disabledHint != null) {
            Text(disabledHint, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
        }
    }
    if (open) {
        OptionSheet(
            title = label,
            options = options,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(horizontal = spacing.xl, vertical = spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
                modifier = Modifier.padding(bottom = spacing.md),
            )
            LazyColumn(contentPadding = PaddingValues(vertical = spacing.xs)) {
                items(options) { (value, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GvColors.Text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = spacing.lg, horizontal = spacing.sm),
                    )
                }
            }
        }
    }
}

/** Calendar picker with the colour swatch, since that is what identifies one in the grid. */
@Composable
internal fun CalendarField(
    label: String,
    calendars: List<GoogleCalendar>,
    selectedId: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.clickable { open = true } else Modifier)
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(selected?.color) ?: GvColors.Primary),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = selected?.summary ?: "Pick a calendar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) GvColors.Text else GvColors.TextMuted,
                )
                selected?.let {
                    Text(it.account_email, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
                }
            }
            if (enabled) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GvColors.TextMuted)
            }
        }
    }
    if (open) {
        CalendarPickerSheet(
            title = label,
            calendars = calendars,
            onSelect = { onSelect(it.id); open = false },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarPickerSheet(
    title: String,
    calendars: List<GoogleCalendar>,
    onSelect: (GoogleCalendar) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(horizontal = spacing.xl, vertical = spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
                modifier = Modifier.padding(bottom = spacing.md),
            )
            if (calendars.isEmpty()) {
                Text(
                    text = "No writable calendar. Connect a Google account, or turn one on in Calendars.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            }
            LazyColumn(contentPadding = PaddingValues(vertical = spacing.xs)) {
                items(calendars, key = { it.id }) { calendar ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(calendar) }
                            .padding(vertical = spacing.lg, horizontal = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(calendar.color) ?: GvColors.Primary),
                        )
                        Column {
                            Text(calendar.summary, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
                            Text(
                                calendar.account_email,
                                style = MaterialTheme.typography.labelSmall,
                                color = GvColors.TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
