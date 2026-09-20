package com.gv.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.HabitWithLog
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import com.gv.app.ui.theme.TimerDisplay
import kotlinx.coroutines.delay

/**
 * One habit for one day. [optimistic] is a value just entered and not yet confirmed: the card
 * shows it at once and recomputes its progress from it (see `HabitLogic.kt`), so a tap is felt
 * before the server has answered.
 */
@Composable
fun HabitCard(
    habit: HabitWithLog,
    optimistic: Double?,
    onAdjust: (delta: Double) -> Unit,
    onSetValue: (value: Double) -> Unit,
    onEdit: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val hasTargets = hasTarget(habit)
    val period = optimisticPeriodValue(habit, optimistic)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        HeaderRow(habit, onEdit)

        if (!habit.description.isNullOrBlank()) {
            Text(
                text = habit.description,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }

        AdjustRow(
            value = displayValue(habit, optimistic),
            onAdjust = onAdjust,
            onSetValue = onSetValue,
        )

        if (hasTargets) {
            ProgressBar(habit.target_min, habit.target_max, period)
            Text(
                text = progressText(habit.target_min, habit.target_max, period),
                style = MaterialTheme.typography.labelMedium,
                color = GvColors.TextMuted,
            )
            StreakRow(
                currentStreak = habit.current_streak,
                longestStreak = habit.longest_streak,
            )
        } else if (habit.frequency != "daily") {
            Text(
                text = "${habit.frequency}: ${formatHabitValue(period)}",
                style = MaterialTheme.typography.labelMedium,
                color = GvColors.TextMuted,
            )
        }
    }
}

@Composable
private fun HeaderRow(habit: HabitWithLog, onEdit: () -> Unit) {
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
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit habit",
                tint = GvColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
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

/** How long typing has to pause before the number is taken as entered. */
private const val TYPED_IDLE_MS = 800L

@Composable
private fun AdjustRow(
    value: Double,
    onAdjust: (Double) -> Unit,
    onSetValue: (Double) -> Unit,
) {
    val spacing = LocalSpacing.current
    var text by remember(value) { mutableStateOf(formatHabitValue(value)) }

    // A number is committed when typing pauses or the keyboard's Done is pressed — never per
    // keystroke, which would log "1" on the way to "12". A comma is a decimal point on a phone
    // set to a Spanish locale.
    fun commit() {
        val typed = text.trim().replace(',', '.').toDoubleOrNull() ?: return
        if (typed != value) onSetValue(typed)
    }
    LaunchedEffect(text) {
        if (text == formatHabitValue(value)) return@LaunchedEffect
        delay(TYPED_IDLE_MS)
        commit()
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
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TimerDisplay.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
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
private fun ProgressBar(targetMin: Double?, targetMax: Double?, period: Double) {
    val color = when {
        exceeded(targetMax, period) -> GvColors.Danger
        targetMet(targetMin, targetMax, period) && period > 0.0 -> GvColors.Success
        else -> GvColors.Primary
    }
    LinearProgressIndicator(
        progress = { progressFraction(targetMin, targetMax, period) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        color = color,
        trackColor = GvColors.Border,
    )
}

@Composable
private fun StreakRow(currentStreak: Int, longestStreak: Int) {
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
private fun StreakItem(icon: ImageVector, value: Int, tint: Color, label: String) {
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
