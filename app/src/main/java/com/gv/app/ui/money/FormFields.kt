package com.gv.app.ui.money

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Pieces ----------

@Composable
internal fun TypeSelector(type: String, onChange: (String) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TypeChip("Income", "income", type, GvColors.Success, onChange, Modifier.weight(1f))
        TypeChip("Expense", "expense", type, GvColors.Danger, onChange, Modifier.weight(1f))
        TypeChip("Transfer", "transfer", type, GvColors.Secondary, onChange, Modifier.weight(1f))
    }
}

@Composable
internal fun TypeChip(
    label: String,
    value: String,
    selected: String,
    accent: androidx.compose.ui.graphics.Color,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = selected == value
    val bg = if (isActive) accent.copy(alpha = 0.18f) else GvColors.Bg
    val border = if (isActive) accent else GvColors.BorderLight
    val textColor = if (isActive) accent else GvColors.TextMuted
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onChange(value) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun GvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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

@Composable
fun <T> DropdownField(
    label: String,
    selectedLabel: String,
    error: Boolean,
    items: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    val borderColor = if (error) GvColors.Danger else GvColors.BorderLight

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (error) GvColors.Danger else GvColors.TextMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = GvColors.TextMuted,
            )
        }
    }

    if (open) {
        DropdownPicker(
            title = label,
            items = items,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownPicker(
    title: String,
    items: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
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
            if (items.isEmpty()) {
                Text(
                    text = "No options",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = spacing.xs),
                ) {
                    items(items) { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(value) }
                                .padding(vertical = spacing.lg, horizontal = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GvColors.Text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    value: LocalDateTime,
    onChange: (LocalDateTime) -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
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
                text = value.format(DateDisplayFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (open) {
        val initialMillis = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onChange(LocalDateTime.of(newDate, value.toLocalTime()))
                    }
                    open = false
                }) {
                    Text("OK", color = GvColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = GvColors.BgLight,
            ),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val DateDisplayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.UK)
