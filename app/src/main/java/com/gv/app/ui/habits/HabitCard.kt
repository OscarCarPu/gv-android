package com.gv.app.ui.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import com.gv.app.ui.theme.TimerDisplay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habit: HabitWithLog,
    onAdjust: (delta: Double) -> Unit,
    onSetValue: (value: Double) -> Unit,
    onLongPress: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val hasTargets = habit.target_min != null || habit.target_max != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            )
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        HeaderRow(habit)

        if (!habit.description.isNullOrBlank()) {
            Text(
                text = habit.description,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }

        AdjustRow(
            value = habit.log_value,
            onAdjust = onAdjust,
            onSetValue = onSetValue,
        )

        if (hasTargets) {
            ProgressBar(
                periodValue = habit.period_value,
                targetMin = habit.target_min,
                targetMax = habit.target_max,
            )
            ProgressText(
                periodValue = habit.period_value,
                targetMin = habit.target_min,
                targetMax = habit.target_max,
            )
            StreakRow(
                currentStreak = habit.current_streak,
                longestStreak = habit.longest_streak,
            )
        }
    }
}

@Composable
private fun HeaderRow(habit: HabitWithLog) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = habit.name,
            style = MaterialTheme.typography.titleLarge,
            color = GvColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (habit.recording_required) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "Recording required",
                tint = GvColors.Warning,
                modifier = Modifier.size(18.dp),
            )
        }
        if (habit.frequency != "daily") {
            FrequencyPill(habit.frequency)
        }
    }
}

@Composable
private fun FrequencyPill(frequency: String) {
    Text(
        text = frequency.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelMedium,
        color = GvColors.Secondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(GvColors.Secondary.copy(alpha = 0.15f))
            .border(1.dp, GvColors.Secondary.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun AdjustRow(
    value: Double?,
    onAdjust: (Double) -> Unit,
    onSetValue: (Double) -> Unit,
) {
    val spacing = LocalSpacing.current
    val displayed = value ?: 0.0
    var text by remember(displayed) { mutableStateOf(formatValue(displayed)) }

    LaunchedEffect(displayed) {
        text = formatValue(displayed)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterHorizontally),
    ) {
        IconButton(
            onClick = { onAdjust(-1.0) },
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = GvColors.Bg,
                contentColor = GvColors.Primary,
            ),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }

        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toDoubleOrNull()?.let { onSetValue(it) }
            },
            singleLine = true,
            textStyle = TimerDisplay.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .width(120.dp)
                .height(56.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GvColors.Text,
                unfocusedTextColor = GvColors.Text,
                focusedBorderColor = GvColors.Primary,
                unfocusedBorderColor = GvColors.BorderLight,
                cursorColor = GvColors.Primary,
            ),
        )

        IconButton(
            onClick = { onAdjust(1.0) },
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = GvColors.Bg,
                contentColor = GvColors.Primary,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}

@Composable
private fun ProgressBar(
    periodValue: Double,
    targetMin: Double?,
    targetMax: Double?,
) {
    val max = targetMax ?: targetMin ?: 1.0
    val safeMax = if (max <= 0.0) 1.0 else max
    val progress = (periodValue / safeMax).toFloat().coerceIn(0f, 1f)

    val inRange = (targetMin == null || periodValue >= targetMin) &&
        (targetMax == null || periodValue <= targetMax)
    val exceeded = targetMax != null && periodValue > targetMax

    val color = when {
        exceeded -> GvColors.Danger
        inRange && periodValue > 0.0 -> GvColors.Success
        else -> GvColors.Primary
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        color = color,
        trackColor = GvColors.Border,
    )
}

@Composable
private fun ProgressText(
    periodValue: Double,
    targetMin: Double?,
    targetMax: Double?,
) {
    val rangeText = when {
        targetMin != null && targetMax != null -> "${formatValue(targetMin)}-${formatValue(targetMax)}"
        targetMin != null -> "≥ ${formatValue(targetMin)}"
        targetMax != null -> "≤ ${formatValue(targetMax)}"
        else -> ""
    }
    Text(
        text = "${formatValue(periodValue)} ($rangeText)",
        style = MaterialTheme.typography.labelMedium,
        color = GvColors.TextMuted,
    )
}

@Composable
private fun StreakRow(currentStreak: Int, longestStreak: Int) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakItem(
            icon = Icons.Outlined.LocalFireDepartment,
            value = currentStreak,
            tint = if (currentStreak > 0) GvColors.Warning else GvColors.TextMuted,
            label = "current",
        )
        Spacer(Modifier.weight(1f))
        StreakItem(
            icon = Icons.Outlined.EmojiEvents,
            value = longestStreak,
            tint = GvColors.TextMuted,
            label = "best",
        )
    }
}

@Composable
private fun StreakItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

private fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
