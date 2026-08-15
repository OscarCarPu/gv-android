package com.gv.app.ui.domotics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.LightState
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import kotlin.math.roundToInt

/** Nudge per button press. Larger than the slider's own step — a press should go somewhere. */
private const val BRIGHTNESS_STEP = 5
private const val TEMP_STEP = 100

@Composable
fun LightCard(
    light: LightState,
    onTogglePower: () -> Unit,
    onBrightness: (Int) -> Unit,
    onColorTemp: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    val enabled = light.online

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GvColors.Surface)
            .border(1.dp, GvColors.Border, RoundedCornerShape(16.dp))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (light.online) GvColors.Success else GvColors.Danger),
                )
                Column {
                    Text(
                        light.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = GvColors.Text,
                    )
                    Text(
                        light.model,
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.TextMuted,
                    )
                }
            }
            Switch(
                checked = light.power,
                onCheckedChange = { onTogglePower() },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GvColors.Primary,
                    uncheckedThumbColor = GvColors.TextMuted,
                    uncheckedTrackColor = GvColors.Bg,
                    uncheckedBorderColor = GvColors.Border,
                ),
            )
        }

        ValueControl(
            label = "Brightness",
            value = light.brightness.roundToInt(),
            unit = "%",
            range = 0..100,
            step = BRIGHTNESS_STEP,
            enabled = enabled,
            trackColors = brightnessTrackColors(GvColors.Bg, kelvinToColor(light.colorTemp)),
            onValue = onBrightness,
        )

        if (light.supportsColorTemp) {
            ValueControl(
                label = "Temperature",
                value = light.colorTemp.roundToInt(),
                unit = "K",
                range = light.minColorTemp.roundToInt()..light.maxColorTemp.roundToInt(),
                step = TEMP_STEP,
                enabled = enabled,
                trackColors = TemperatureTrackColors,
                onValue = onColorTemp,
            )
        }

        light.error?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GvColors.Danger)
        }
    }
}

/**
 * Label + editable readout, then −/slider/+.
 *
 * The readout is the text field rather than a separate one: the number is already on screen,
 * and duplicating it invites the two disagreeing. Typed values commit on done/blur, never per
 * keystroke — each commit is a BLE write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueControl(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    trackColors: List<Color>,
    onValue: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current

    // Local text so typing isn't fought by incoming polls; resynced whenever the truth moves.
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { text = value.toString() }

    fun commit() {
        val parsed = text.toIntOrNull()
        val next = if (parsed == null) value else parsed.coerceIn(range.first, range.last)
        text = next.toString()
        if (next != value) onValue(next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.TextMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(5) },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge.copy(
                        color = GvColors.Text,
                        textAlign = TextAlign.End,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .width(72.dp)
                        // Commit on blur as well as Done: tapping elsewhere is how most people
                        // leave a field, and a typed value that silently evaporates reads as a bug.
                        .onFocusChanged { if (!it.isFocused) commit() },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GvColors.Primary,
                        unfocusedBorderColor = GvColors.Border,
                        focusedContainerColor = GvColors.Bg,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = GvColors.TextMuted,
                    modifier = Modifier.padding(start = spacing.xxs),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            StepButton(Icons.Filled.Remove, "Decrease $label", enabled) {
                onValue((value - step).coerceIn(range.first, range.last))
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValue(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.White),
                // The whole track is the gradient, with no active/inactive split: the point is
                // to show what the setting looks like, and a half-grey bar hides exactly that.
                track = { GradientTrack(trackColors, enabled) },
            )
            StepButton(Icons.Filled.Add, "Increase $label", enabled) {
                onValue((value + step).coerceIn(range.first, range.last))
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) GvColors.TextMuted else GvColors.TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Slider track painted with a horizontal gradient.
 *
 * Material's own track is a two-tone active/inactive bar, which cannot show a colour ramp.
 * Drawing it directly is the only way to get the same warm-to-cool sweep the web has.
 */
@Composable
private fun GradientTrack(colors: List<Color>, enabled: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            alpha = if (enabled) 1f else 0.4f,
        )
    }
}
