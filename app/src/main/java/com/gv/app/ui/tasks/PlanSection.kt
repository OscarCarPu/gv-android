package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a plan row can ask for, bundled so the section does not thread a dozen lambdas. */
internal class PlanActions(
    val onToggleBlock: (PlanBlockResponse) -> Unit,
    val onEditBlock: (PlanBlockResponse) -> Unit,
    val onDeleteBlock: (PlanBlockResponse) -> Unit,
    val onStartTimer: (taskId: Int) -> Unit,
    val onAssignTimer: (taskId: Int) -> Unit,
    val onStopAndStart: (taskId: Int) -> Unit,
    val onOpenEntry: (entryId: Int) -> Unit,
)

private val CapacityDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.UK)

/**
 * Today's Plan as web has it: a free-time strip for the next seven days (tap a day to see and
 * edit that day's blocks), then one agenda around a "now" line — what really happened above it,
 * what is still planned below.
 */
internal fun LazyListScope.planSection(
    state: PlanUiState,
    timerRunning: Boolean,
    actions: PlanActions,
    onSelectDate: (LocalDate) -> Unit,
    onBackToToday: () -> Unit,
    onNewBlock: () -> Unit,
    onClearFuture: () -> Unit,
    onCommitments: () -> Unit,
) {
    item(key = "plan-header") {
        PlanHeader(state, onBackToToday, onNewBlock, onClearFuture)
    }

    if (state.freeBusy.isNotEmpty()) {
        item(key = "plan-capacity") {
            CapacityStrip(state.freeBusy, selected = state.date, onSelect = onSelectDate, onCommitments = onCommitments)
        }
    }

    if (!state.isToday) {
        otherDay(state, actions)
        return
    }

    if (!state.loaded) {
        item(key = "plan-unavailable") { EmptyHint("Plan could not be loaded") }
        return
    }

    state.summary?.let { summary -> item(key = "plan-summary") { PlanSummaryCard(summary) } }

    if (!state.hasContent) {
        item(key = "plan-empty") { EmptyHint("No blocks for today") }
        return
    }

    val items = state.timeline?.items.orEmpty()
    items.forEachIndexed { i, entry ->
        item(key = planItemKey(entry, i)) { PlanItemRow(entry, timerRunning, actions) }
    }
}

/** Entries and blocks can share numeric ids, so the key is namespaced by what the row is. */
private fun planItemKey(item: PlanItem, index: Int): String = when (item) {
    is PlanItem.Actual -> "a${item.entryId}"
    is PlanItem.Rest -> "b${item.block.id}-rest"
    is PlanItem.Skipped -> "b${item.block.id}-skipped"
    is PlanItem.Planned -> "b${item.block.id}-planned"
    is PlanItem.Gap -> "gap-$index"
    is PlanItem.Now -> "now"
}

private fun LazyListScope.otherDay(state: PlanUiState, actions: PlanActions) {
    val blocks = state.otherDayBlocks
    when {
        blocks == null -> item(key = "plan-loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GvColors.Primary, modifier = Modifier.size(24.dp))
            }
        }
        blocks.isEmpty() -> item(key = "plan-none") { EmptyHint("No blocks for this day") }
        else -> blocks.forEach { b ->
            item(key = "other-${b.id}") {
                BlockRow(
                    block = b,
                    detail = b.note,
                    free = b.task_id == null,
                    trailing = { EditDelete(onEdit = { actions.onEditBlock(b) }, onDelete = { actions.onDeleteBlock(b) }) },
                )
            }
        }
    }
}

// ---------- Header, capacity strip, summary ----------

@Composable
private fun PlanHeader(
    state: PlanUiState,
    onBackToToday: () -> Unit,
    onNewBlock: () -> Unit,
    onClearFuture: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = if (state.isToday) "Today's Plan" else "Plan · ${state.date.format(CapacityDayFormatter)}",
            style = MaterialTheme.typography.titleMedium,
            color = GvColors.Text,
        )
        if (!state.isToday) {
            IconButton(onClick = onBackToToday, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Restore, contentDescription = "Back to today", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.weight(1f))
        if (state.isToday) {
            IconButton(onClick = onClearFuture, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear future blocks", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        SmallButton("Block", onNewBlock, icon = Icons.Filled.Add)
    }
}

@Composable
private fun CapacityStrip(
    days: List<DayFreeBusy>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onCommitments: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Free time (next 7 days)", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted, modifier = Modifier.weight(1f))
            IconButton(onClick = onCommitments, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Recurring commitments", tint = GvColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            days.forEach { day ->
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@forEach
                CapacityDay(day, date, active = date == selected, modifier = Modifier.weight(1f)) { onSelect(date) }
            }
        }
    }
}

/** Share of a day's capacity still free, 0–100. Zero when the capacity is missing or zero. */
internal fun freePercent(day: DayFreeBusy): Float {
    val capacity = day.capacity_hours.toDoubleOrNull() ?: return 0f
    val free = day.free_hours.toDoubleOrNull() ?: return 0f
    if (capacity <= 0) return 0f
    return (free / capacity * 100).coerceIn(0.0, 100.0).toFloat()
}

@Composable
private fun CapacityDay(day: DayFreeBusy, date: LocalDate, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pct = freePercent(day)
    // Tight or full days are the ones that explain why a task is urgent, so they change colour.
    val fill = when {
        pct <= 0f -> GvColors.Danger
        pct < 25f -> GvColors.Warning
        else -> GvColors.Primary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) GvColors.Primary.copy(alpha = 0.12f) else GvColors.BgLight)
            .border(1.dp, if (active) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(date.format(CapacityDayFormatter), style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted, maxLines = 1)
        Box(
            modifier = Modifier.width(10.dp).height(36.dp).clip(RoundedCornerShape(3.dp)).background(GvColors.Border),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(pct / 100f).background(fill))
        }
        Text("${day.free_hours.toDoubleOrNull()?.toInt() ?: 0}h", style = MaterialTheme.typography.labelSmall, color = GvColors.Text)
    }
}

@Composable
private fun PlanSummaryCard(summary: PlanSummary) {
    val spacing = LocalSpacing.current
    val barColor = if (summary.estimatedReached) GvColors.Success else GvColors.Primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text("Est.", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted, modifier = Modifier.width(36.dp))
            // Two-tone bar: solid is already logged, the lighter part is projected from the plan.
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(GvColors.Border)) {
                Box(Modifier.fillMaxWidth(summary.estimatedPct).fillMaxHeight().background(barColor.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth(summary.donePct).fillMaxHeight().background(barColor))
            }
            Text(
                "${formatDurationShort(summary.doneSeconds)} → ${formatDurationShort(summary.estimatedSeconds)} / ${formatDurationShort(summary.dailyTargetSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                color = GvColors.Text,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text("Free", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted, modifier = Modifier.width(36.dp))
            Text(formatDurationShort(summary.freeSeconds), style = MaterialTheme.typography.labelMedium, color = GvColors.Text)
            if (summary.skippedSeconds > 0) {
                MetaPill("${formatDurationShort(summary.skippedSeconds)} skipped", GvColors.Warning)
            }
        }
    }
}

// ---------- Timeline rows ----------

@Composable
private fun PlanItemRow(item: PlanItem, timerRunning: Boolean, actions: PlanActions) {
    when (item) {
        is PlanItem.Now -> NowDivider(formatClock(item.ms))
        is PlanItem.Gap -> GapDivider("${formatClock(item.fromMs)} – ${formatClock(item.toMs)} · ${formatDurationShort(item.seconds)} unaccounted")
        is PlanItem.Skipped -> BlockRow(
            block = item.block,
            detail = "${formatDurationShort(item.seconds)} · " + when {
                item.workedThrough -> "worked through"
                item.movedElsewhere -> "done at another time"
                else -> "not done"
            },
            dim = true,
            trailing = { EditDelete(onEdit = { actions.onEditBlock(item.block) }, onDelete = { actions.onDeleteBlock(item.block) }) },
        )
        is PlanItem.Rest -> BlockRow(
            block = item.block,
            detail = "${formatDurationShort(item.seconds)} · as planned",
            free = true,
            trailing = { Icon(Icons.Filled.Check, contentDescription = null, tint = GvColors.Success, modifier = Modifier.size(18.dp)) },
        )
        is PlanItem.Actual -> ActualRow(item, onClick = { actions.onOpenEntry(item.entryId) })
        is PlanItem.Planned -> PlannedRow(item, timerRunning, actions)
    }
}

@Composable
private fun NowDivider(time: String) {
    val spacing = LocalSpacing.current
    Row(Modifier.fillMaxWidth().padding(vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = GvColors.Primary)
        Text(time, style = MaterialTheme.typography.labelMedium, color = GvColors.Primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = spacing.md))
        HorizontalDivider(Modifier.weight(1f), color = GvColors.Primary)
    }
}

@Composable
private fun GapDivider(text: String) {
    val spacing = LocalSpacing.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = GvColors.BorderLight)
        Text(text, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted, modifier = Modifier.padding(horizontal = spacing.md))
        HorizontalDivider(Modifier.weight(1f), color = GvColors.BorderLight)
    }
}

@Composable
private fun EditDelete(onEdit: () -> Unit, onDelete: () -> Unit) {
    Row {
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit block", tint = GvColors.TextMuted, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete block", tint = GvColors.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** The frame every plan row shares: a time column, a body, and whatever sits at the right. */
@Composable
private fun RowFrame(
    from: String,
    to: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    below: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(from, style = MaterialTheme.typography.labelMedium, color = GvColors.Text)
                Text(to, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
            }
            Column(Modifier.weight(1f)) { body() }
            trailing()
        }
        below?.invoke()
    }
}

@Composable
private fun BlockRow(
    block: PlanBlockResponse,
    detail: String?,
    free: Boolean = block.task_id == null,
    dim: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    RowFrame(
        from = formatClock(block.started_at),
        to = formatClock(block.ended_at),
        accent = if (free) GvColors.Secondary else GvColors.BorderLight,
        trailing = trailing,
    ) {
        Text(
            block.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (dim) GvColors.TextMuted else GvColors.Text,
            textDecoration = if (dim) TextDecoration.LineThrough else null,
        )
        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
    }
}

/** Work that really happened. Tapping opens the entry to correct it. */
@Composable
private fun ActualRow(item: PlanItem.Actual, onClick: () -> Unit) {
    val shortfall = item.plannedSeconds - item.seconds
    RowFrame(
        from = formatClock(item.startedMs),
        to = if (item.running) "now" else formatClock(item.endedMs),
        accent = if (item.running) GvColors.Primary else GvColors.Success,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(item.taskName, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
        val time = if (item.block != null) "${formatDurationShort(item.seconds)} / ${formatDurationShort(item.plannedSeconds)}" else formatDurationShort(item.seconds)
        val verdict = when {
            item.offScheduleBlock != null -> " · planned for ${formatClock(item.offScheduleBlock.started_at)}"
            // A running entry has not had its chance yet; calling it short is noise.
            shortfall > 120 && !item.running -> " · ${formatDurationShort(shortfall)} short"
            else -> ""
        }
        val project = item.projectName?.let { " · $it" }.orEmpty()
        val comment = item.comment?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        Text(time + verdict + project + comment, style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
    }
}

/** Intent still to come, with the actions that move it forward. */
@Composable
private fun PlannedRow(item: PlanItem.Planned, timerRunning: Boolean, actions: PlanActions) {
    val spacing = LocalSpacing.current
    val b = item.block
    val finished = b.task_finished_at != null
    val started = b.task_started_at != null
    val taskId = b.task_id
    RowFrame(
        from = formatClock(b.started_at),
        to = formatClock(b.ended_at),
        accent = when {
            item.current -> GvColors.Primary
            b.task_id == null -> GvColors.Secondary
            else -> GvColors.BorderLight
        },
        trailing = { EditDelete(onEdit = { actions.onEditBlock(b) }, onDelete = { actions.onDeleteBlock(b) }) },
        below = if (taskId == null) null else ({
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (finished) {
                    SmallButton("Done", {}, enabled = false, color = GvColors.Success)
                } else {
                    SmallButton(
                        label = when {
                            !started -> "Start"
                            b.task_type == "recurring" -> "Renew"
                            else -> "Done"
                        },
                        onClick = { actions.onToggleBlock(b) },
                        color = if (started) GvColors.Primary else GvColors.Success,
                    )
                    TimerActions(
                        recurring = b.task_type == "recurring",
                        blocked = false,
                        timerRunning = timerRunning,
                        onStart = { actions.onStartTimer(taskId) },
                        onAssign = { actions.onAssignTimer(taskId) },
                        onStopAndStart = { actions.onStopAndStart(taskId) },
                    )
                }
            }
        }),
    ) {
        Text(b.label, style = MaterialTheme.typography.bodyMedium, color = if (finished) GvColors.TextMuted else GvColors.Text,
            textDecoration = if (finished) TextDecoration.LineThrough else null)
        val left = formatDurationShort(item.remainingSeconds) + if (item.current) " left" else ""
        Text(left + (b.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
    }
}
