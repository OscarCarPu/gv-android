package com.gv.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.ui.common.formatFreeHours
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FreeDayFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.UK)

/**
 * The calendar's side panel, as the web's sidebar: the next seven days' capacity. There are no
 * calendars or accounts to manage any more — events are just a name and a time range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(freeBusy: List<DayFreeBusy>, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
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
                .padding(horizontal = spacing.xl)
                .padding(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text("Calendar", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
            if (freeBusy.isEmpty()) {
                Text("No capacity data yet.", style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
            } else {
                FreeTimePanel(freeBusy)
            }
        }
    }
}

@Composable
private fun FreeTimePanel(days: List<DayFreeBusy>) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Free time (next 7 days)", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
        days.forEach { day ->
            val date = runCatching { java.time.LocalDate.parse(day.date) }.getOrNull()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(date?.format(FreeDayFormatter) ?: day.date, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text, modifier = Modifier.weight(1f))
                Text(formatFreeHours(day.free_hours), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
                Text("/ ${formatFreeHours(day.capacity_hours)}", style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
            }
        }
    }
}
