package com.gv.app.ui.tasks

import com.gv.app.ui.common.SmallButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.TaskByDueDateResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate

// ---------- Small building blocks shared by every task row ----------

@Composable
internal fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun PriorityBadge(priority: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("P$priority", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun MetaPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GvColors.Bg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * The timer buttons every task row shares, in the web's three-way flow: nothing running → one
 * play button; something running → **Assign** re-points the running entry at this task and
 * **Stop Start** finishes it and begins a new one on this task.
 */
@Composable
internal fun TimerActions(
    recurring: Boolean,
    blocked: Boolean,
    timerRunning: Boolean,
    onStart: () -> Unit,
    onAssign: () -> Unit,
    onStopAndStart: () -> Unit,
) {
    val spacing = LocalSpacing.current
    if (timerRunning) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            SmallButton("Assign", onAssign, enabled = !blocked, icon = Icons.AutoMirrored.Filled.ArrowForward)
            SmallButton(
                label = if (recurring) "Renew Start" else "Stop Start",
                onClick = onStopAndStart,
                enabled = !blocked,
                color = GvColors.Success,
                icon = Icons.Filled.PlayArrow,
            )
        }
    } else {
        SmallButton("Timer", onStart, enabled = !blocked, icon = Icons.Filled.PlayArrow)
    }
}

@Composable
internal fun DayDivider(label: String, highlight: Boolean) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = GvColors.BorderLight)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = spacing.md),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = GvColors.BorderLight)
    }
}

// ---------- Due Soon row ----------

/**
 * One Due Soon task: what it is and why it is here, with the web's actions underneath —
 * Start / Done (Renew when recurring) for the task itself, then the timer buttons.
 */
@Composable
internal fun DueTaskRow(
    task: TaskByDueDateResponse,
    timerRunning: Boolean,
    onDetail: () -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onStartTimer: () -> Unit,
    onAssignTimer: () -> Unit,
    onStopAndStart: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val spacing = LocalSpacing.current
    val started = !task.started_at.isNullOrBlank()
    val dueKey = dueDateOf(task)
    val overdue = dueKey != null && dueKey < today.toString()
    val recurring = task.task_type == "recurring"
    val urgency = buildUrgencyPhrase(task, today)

    // Overdue is red; urgent means "should have started", which is a different message and a
    // different colour — and a task that is both stays red.
    val borderColor = when {
        overdue -> GvColors.Danger.copy(alpha = 0.6f)
        task.urgent -> GvColors.Warning.copy(alpha = 0.6f)
        else -> GvColors.BorderLight
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
    ) {
        // Priority strip: urgent tasks read at a glance.
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(priorityColor(task.priority)))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = GvColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (task.blocked) {
                        Icon(Icons.Filled.Block, contentDescription = "Blocked", tint = GvColors.Danger, modifier = Modifier.size(16.dp))
                    }
                }
                task.project_name?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    StatusBadge(statusLabel(task.started_at, task.task_type, task.recurrence), taskTypeColor(task.task_type, started))
                    PriorityBadge(task.priority, priorityColor(task.priority))
                    task.due_at?.let { MetaPill(formatShortDate(it), GvColors.TextMuted) }
                    if (task.time_spent > 0) MetaPill(formatDurationShort(task.time_spent), GvColors.TextMuted)
                }
                urgency?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = GvColors.Warning, modifier = Modifier.size(14.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = GvColors.Warning)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (started) {
                    SmallButton(if (recurring) "Renew" else "Done", onFinish, enabled = !task.blocked)
                } else {
                    SmallButton("Start", onStart, enabled = !task.blocked, color = GvColors.Success)
                }
                TimerActions(
                    recurring = recurring,
                    blocked = task.blocked,
                    timerRunning = timerRunning,
                    onStart = onStartTimer,
                    onAssign = onAssignTimer,
                    onStopAndStart = onStopAndStart,
                )
            }
        }
    }
}
