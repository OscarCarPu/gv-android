package com.gv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gv.app.AppColors

fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

fun formatHours(seconds: Long): String {
    val h = seconds / 3600.0
    return "%.1fh".format(h)
}

@Composable
fun TimeDisplay(seconds: Long, fontSize: TextUnit = 32.sp) {
    Text(
        text = formatElapsed(seconds),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun StatusBadge(startedAt: String?, finishedAt: String?) {
    val (label, color) = when {
        finishedAt != null -> "Terminado" to AppColors.success
        startedAt != null  -> "En curso" to MaterialTheme.colorScheme.primary
        else               -> "Pendiente" to AppColors.muted
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun FrequencyBadge(frequency: String?) {
    val label = when (frequency) {
        "daily"   -> "Diario"
        "weekly"  -> "Semanal"
        "monthly" -> "Mensual"
        else      -> return
    }
    Text(
        text = label,
        color = AppColors.secondary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(AppColors.secondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun TargetProgressBar(
    current: Double,
    targetMin: Double?,
    targetMax: Double?,
    modifier: Modifier = Modifier
) {
    val target = targetMax ?: targetMin ?: return
    if (target <= 0.0) return
    val fraction = (current / target).toFloat().coerceIn(0f, 1f)
    val color = when {
        targetMin != null && current >= targetMin -> AppColors.success
        current >= target * 0.5 -> AppColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(AppColors.muted.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(shape)
                .background(color)
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun SummaryProgressBar(
    current: Long,
    target: Long,
    label: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
    val color = when {
        fraction >= 1f   -> AppColors.success
        fraction >= 0.7f -> MaterialTheme.colorScheme.primary
        else             -> AppColors.muted
    }
    val shape = RoundedCornerShape(4.dp)
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
            Text(
                "${formatHours(current)} / ${formatHours(target)}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.muted
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(shape)
                .background(AppColors.muted.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(shape)
                    .background(color)
            )
        }
    }
}
