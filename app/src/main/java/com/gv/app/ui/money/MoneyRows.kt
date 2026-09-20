package com.gv.app.ui.money

import com.gv.app.domain.model.OverviewTransaction
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import kotlin.math.abs

// ---------- Rows and tiles shared by the tabs ----------

internal enum class TileTone { POS, NEG, NEUTRAL }

internal fun toneOf(v: Double): TileTone =
    if (v > 0) TileTone.POS else if (v < 0) TileTone.NEG else TileTone.NEUTRAL

internal fun signed(v: Double): String {
    val abs = formatMoney(kotlin.math.abs(v))
    return when {
        v > 0 -> "+$abs"
        v < 0 -> "−$abs"
        else -> abs
    }
}

@Composable
internal fun KpiTile(title: String, value: String, tone: TileTone, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val valueColor = when (tone) {
        TileTone.POS -> GvColors.Success
        TileTone.NEG -> GvColors.Danger
        TileTone.NEUTRAL -> GvColors.Text
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun TxRow(
    tx: OverviewTransaction,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sign = amountSign(tx.type)
    val amountColor = when (sign) {
        AmountSign.POS -> GvColors.Success
        AmountSign.NEG -> GvColors.Danger
        AmountSign.NEU -> GvColors.TextMuted
    }
    val prefix = when (sign) {
        AmountSign.POS -> "+"
        AmountSign.NEG -> "−"
        AmountSign.NEU -> ""
    }
    val name = tx.description?.takeIf { it.isNotBlank() } ?: tx.category_name ?: "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        TypeBadge(tx.type)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
            )
            val accountLine = buildString {
                append(tx.account_name)
                if (tx.to_account_name != null) {
                    append(" → ")
                    append(tx.to_account_name)
                }
            }
            Text(
                text = accountLine,
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.TextMuted,
            )
        }
        Text(
            text = prefix + formatMoney(tx.amount),
            style = MaterialTheme.typography.bodyMedium,
            color = amountColor,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = GvColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun TypeBadge(type: String) {
    val (label, color) = when (type) {
        "income" -> "In" to GvColors.Success
        "expense" -> "Out" to GvColors.Danger
        "transfer" -> "Tx" to GvColors.Secondary
        else -> type to GvColors.TextMuted
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
