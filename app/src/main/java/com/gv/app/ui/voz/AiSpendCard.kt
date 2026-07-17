package com.gv.app.ui.voz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.gv.app.domain.model.AiUsage
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Compact "Gasto de IA este mes" card shown at the top of the Voz screen. */
@Composable
fun AiSpendCard(state: UsageState, modifier: Modifier = Modifier) {
    when (state) {
        is UsageState.Hidden -> Unit // a failure with no prior value simply omits the card
        is UsageState.Loading -> CardShell(modifier) {
            Box(
                Modifier.fillMaxWidth().height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = GvColors.Primary, strokeWidth = 2.dp)
            }
        }
        is UsageState.Ready -> AiSpendContent(state.usage, modifier)
    }
}

@Composable
private fun CardShell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
        content = content,
    )
}

@Composable
private fun AiSpendContent(usage: AiUsage, modifier: Modifier) {
    val spacing = LocalSpacing.current
    val hasSpend = usage.total_cost_usd.toBigDecimalOrNull()?.signum() == 1
    CardShell(modifier) {
        Text(
            "Gasto de IA · ${monthLabel(usage.month)}",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
        Text(
            formatUsd(usage.total_cost_usd),
            style = MaterialTheme.typography.headlineMedium,
            color = GvColors.Text,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${usage.interaction_count} interacciones · " +
                "${formatTokens(usage.total_input_tokens + usage.total_output_tokens)} tokens",
            style = MaterialTheme.typography.labelSmall,
            color = GvColors.TextMuted,
        )
        if (hasSpend) {
            Spacer(Modifier.height(spacing.xs))
            DailyBars(usage, Modifier.fillMaxWidth().height(56.dp))
        } else {
            Box(
                Modifier.fillMaxWidth().padding(vertical = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sin gasto este mes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun DailyBars(usage: AiUsage, modifier: Modifier) {
    val ym = runCatching { YearMonth.parse(usage.month) }.getOrNull() ?: YearMonth.now()
    val days = ym.lengthOfMonth()
    val costs = DoubleArray(days)
    usage.by_day.forEach { d ->
        val dom = runCatching { LocalDate.parse(d.date).dayOfMonth }.getOrNull()
        if (dom != null && dom in 1..days) {
            costs[dom - 1] = d.cost_usd.toDoubleOrNull() ?: 0.0
        }
    }
    val maxCost = costs.maxOrNull() ?: 0.0
    val barColor = GvColors.Primary
    val axisColor = GvColors.TextMuted

    Canvas(modifier) {
        drawBaseline(axisColor)
        if (maxCost <= 0.0) return@Canvas
        val gap = 1.5.dp.toPx()
        val topPad = 4.dp.toPx()
        val barW = ((size.width - gap * (days - 1)) / days).coerceAtLeast(1f)
        val radius = CornerRadius(barW / 2f, barW / 2f)
        val baseY = size.height
        for (i in 0 until days) {
            val v = costs[i]
            if (v <= 0.0) continue
            val h = ((v / maxCost) * (size.height - topPad)).toFloat().coerceAtLeast(barW)
            val x = i * (barW + gap)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, baseY - h),
                size = Size(barW, h),
                cornerRadius = radius,
            )
        }
    }
}

private fun DrawScope.drawBaseline(axisColor: androidx.compose.ui.graphics.Color) {
    val y = size.height
    drawLine(
        color = axisColor.copy(alpha = 0.35f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
    )
}

// --- formatting (money via BigDecimal, never Float) ---

private fun formatUsd(raw: String): String {
    val amount = raw.toBigDecimalOrNull() ?: return "$0.00"
    if (amount.signum() == 0) return "$0.00"
    val cents = amount.setScale(2, RoundingMode.HALF_UP)
    if (cents.signum() == 0) return "<$0.01" // positive but rounds below a cent
    return "$" + cents.toPlainString()
}

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.UK, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.UK, "%.1fk", n / 1_000.0)
    else -> n.toString()
}

private fun monthLabel(yyyyMm: String): String = runCatching {
    val ym = YearMonth.parse(yyyyMm)
    val m = ym.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() }
    "$m ${ym.year}"
}.getOrDefault(yyyyMm)
