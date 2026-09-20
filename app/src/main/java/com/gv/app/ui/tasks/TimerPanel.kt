package com.gv.app.ui.tasks

import com.gv.app.ui.common.SmallButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gv.app.domain.model.TimeEntrySummaryResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import com.gv.app.ui.theme.TimerDisplay
import kotlinx.coroutines.delay

/**
 * The timer, at the top of the Today tab as on the web: the pen picks a task, the name opens
 * it, and Start / Stop sits beside the clock. A timer here always belongs to a task (the server
 * issues the entry), so with nothing running Start opens the picker and picking a task starts.
 *
 * The chevron unfolds the comment, the start time, Cancel, and today / week progress.
 */
@Composable
internal fun TimerPanel(
    timer: TimerState,
    summary: TimeEntrySummaryResponse?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPickTask: () -> Unit,
    onOpenTask: (taskId: Int) -> Unit,
    onStop: (comment: String?) -> Unit,
    onCancel: () -> Unit,
    onCommentChange: (String) -> Unit,
    onEditStart: (String) -> Unit,
    onAgenda: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val active = timer.active

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, if (timer.isRunning) GvColors.Primary.copy(alpha = 0.5f) else GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Two rows: the task name needs the width, and on a phone six controls in one line
        // would leave it about sixty points.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            IconButton(onClick = onPickTask, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Select task", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (active != null) onOpenTask(active.taskId) else onPickTask() }
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = active?.taskName ?: "Select task",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active != null) GvColors.Text else GvColors.TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                active?.projectName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse timer details" else "Expand timer details",
                    tint = GvColors.TextMuted,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatHhMmSs(timer.elapsedSeconds),
                style = TimerDisplay.copy(fontSize = 22.sp),
                color = if (timer.isRunning) GvColors.Primary else GvColors.TextMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = spacing.sm),
            )
            if (timer.isRunning) {
                SmallButton("Stop", { onStop(null) }, color = GvColors.Danger, icon = Icons.Filled.Stop)
            } else {
                SmallButton("Start", onPickTask, color = GvColors.Success, icon = Icons.Filled.PlayArrow)
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                if (active != null) TimerDetails(active, onCommentChange, onEditStart, onCancel)
                summary?.let { ProgressSummary(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAgenda) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = GvColors.Primary, modifier = Modifier.size(18.dp))
                        Text("  Agenda", color = GvColors.Primary)
                    }
                }
            }
        }
    }
}

/** Comment (saved as you type), when it started, and Cancel — which deletes the entry. */
@Composable
private fun TimerDetails(
    active: com.gv.app.domain.model.ActiveTimer,
    onCommentChange: (String) -> Unit,
    onEditStart: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var comment by remember(active.serverId) { mutableStateOf(active.comment.orEmpty()) }
    val startedAt = remember(active.startedAt) { parseIso(active.startedAt) ?: java.time.LocalDateTime.now() }

    // Debounced, and only when it differs from what the server already holds: opening the panel
    // must not rewrite the comment with itself.
    LaunchedEffect(comment) {
        if (comment == active.comment.orEmpty()) return@LaunchedEffect
        delay(500)
        onCommentChange(comment)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("Comment…", color = GvColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    cursorColor = GvColors.Primary,
                ),
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel entry", tint = GvColors.Danger)
            }
        }
        DateTimeField("Started", startedAt) { onEditStart(localDateTimeToIsoUtc(it)) }
    }
}

/** Today and this week against their targets, coloured by how close the day is to done. */
@Composable
internal fun ProgressSummary(summary: TimeEntrySummaryResponse) {
    val spacing = LocalSpacing.current
    val dailyTarget = summary.daily_target_seconds.coerceAtLeast(1L)
    val weeklyTarget = summary.weekly_target_seconds.coerceAtLeast(1L)
    val todayColor = when {
        summary.today >= dailyTarget * 11 / 12 -> GvColors.Success
        summary.today >= dailyTarget * 5 / 6 -> GvColors.Warning
        else -> GvColors.Primary
    }
    val weekColor = if (summary.week >= weeklyTarget) GvColors.Success else GvColors.Primary

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        ProgressRow("Today", (summary.today.toFloat() / dailyTarget).coerceIn(0f, 1f), todayColor, "${formatDurationShort(summary.today)} / ${formatDurationShort(summary.daily_target_seconds)}")
        ProgressRow("Week", (summary.week.toFloat() / weeklyTarget).coerceIn(0f, 1f), weekColor, "${formatDurationShort(summary.week)} / ${formatDurationShort(summary.weekly_target_seconds)}")
    }
}

@Composable
private fun ProgressRow(label: String, progress: Float, color: Color, value: String) {
    val spacing = LocalSpacing.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted, modifier = Modifier.widthIn(min = 44.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
            color = color,
            trackColor = GvColors.Border,
        )
        Text(value, style = MaterialTheme.typography.labelMedium, color = GvColors.Text)
    }
}
