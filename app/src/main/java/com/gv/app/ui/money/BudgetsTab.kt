package com.gv.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.BudgetItem
import com.gv.app.domain.model.BudgetMonth
import com.gv.app.ui.common.DayDivider
import com.gv.app.ui.common.EmptyHint
import com.gv.app.ui.common.ShowMore
import com.gv.app.ui.common.SmallButton
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate

/**
 * The web's budgets page: a month navigator, four tiles (Spent, Earned, Balance, Off budget),
 * then the monthly rows grouped Expenses / Income and the year's yearly ones. A tap on a row
 * opens the transactions it counted; the pen edits the budget.
 */
@Composable
internal fun BudgetsTab(
    data: BudgetMonth,
    month: String,
    expanded: ExpandedBudget?,
    onMonth: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (BudgetItem) -> Unit,
    onToggle: (BudgetItem) -> Unit,
    onShowMore: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val todayKey = LocalDate.now().toString()
    val isCurrent = month == currentBudgetMonth()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "month-nav") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(currentBudgetMonth()) }, enabled = !isCurrent) {
                    Icon(
                        Icons.Filled.Today,
                        contentDescription = "Go to this month",
                        tint = if (isCurrent) Color.Transparent else GvColors.TextMuted,
                    )
                }
                IconButton(onClick = { onMonth(shiftBudgetMonth(month, -1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month", tint = GvColors.TextMuted)
                }
                Text(
                    formatBudgetMonth(month),
                    style = MaterialTheme.typography.titleMedium,
                    color = GvColors.Text,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onMonth(shiftBudgetMonth(month, 1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month", tint = GvColors.TextMuted)
                }
                Box(Modifier.size(48.dp))
            }
        }
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Budgets", style = MaterialTheme.typography.titleMedium, color = GvColors.Text, modifier = Modifier.weight(1f))
                SmallButton("Budget", onNew, icon = Icons.Filled.Add)
            }
        }
        item(key = "tiles") { BudgetTiles(data) }

        if (hasNoBudgets(data)) {
            item(key = "empty") { EmptyHint("No budgets for this month") }
            return@LazyColumn
        }

        val pace = runningPace(data.month_progress)
        groupBudgetsByType(data.items).forEach { group ->
            if (group.items.isEmpty()) return@forEach
            item(key = "label-m-${group.type}") { GroupLabel(group.label) }
            budgetRows(group.items, month, pace, expanded, todayKey, onEdit, onToggle, onShowMore)
        }

        if (hasYearlyBudgets(data)) {
            item(key = "year-header") {
                Row(Modifier.fillMaxWidth().padding(top = spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Yearly · ${data.yearly.year}",
                        style = MaterialTheme.typography.labelLarge,
                        color = GvColors.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${formatMoney(data.yearly.expense.actual)} of ${formatMoney(data.yearly.expense.budgeted)} spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = GvColors.TextMuted,
                    )
                }
            }
            val yearPace = runningPace(data.yearly.year_progress)
            groupBudgetsByType(data.yearly.items).forEach { group ->
                if (group.items.isNotEmpty()) {
                    budgetRows(group.items, month, yearPace, expanded, todayKey, onEdit, onToggle, onShowMore)
                }
            }
        }
    }
}

private fun LazyListScope.budgetRows(
    items: List<BudgetItem>,
    month: String,
    pace: Double?,
    expanded: ExpandedBudget?,
    todayKey: String,
    onEdit: (BudgetItem) -> Unit,
    onToggle: (BudgetItem) -> Unit,
    onShowMore: () -> Unit,
) {
    items.forEach { item ->
        val key = budgetKey(month, item)
        val open = expanded?.key == key
        item(key = "row-$key") { BudgetRow(item, pace, open, onToggle = { onToggle(item) }, onEdit = { onEdit(item) }) }
        if (!open || expanded == null) return@forEach

        when {
            expanded.loading && expanded.transactions.isEmpty() ->
                item(key = "tx-loading-$key") { Indented(item.depth) { EmptyHint("Loading…") } }
            expanded.transactions.isEmpty() ->
                item(key = "tx-empty-$key") { Indented(item.depth) { EmptyHint("No transactions") } }
            else -> {
                val visible = expanded.visible
                visible.forEachIndexed { i, tx ->
                    val day = tx.occurred_at.take(10)
                    if (i == 0 || day != visible[i - 1].occurred_at.take(10)) {
                        item(key = "tx-day-$key-$i") { Indented(item.depth) { DayDivider(formatDayLabel(day), day == todayKey) } }
                    }
                    item(key = "tx-$key-${tx.id}") { Indented(item.depth) { TxRow(tx) } }
                }
                if (expanded.remaining > 0) {
                    item(key = "tx-more-$key") { Indented(item.depth) { ShowMore(expanded.remaining, onShowMore) } }
                }
            }
        }
    }
}

/** A budget nested under another is pushed in by its depth, and so are the transactions under it. */
@Composable
private fun Indented(depth: Int, content: @Composable () -> Unit) {
    Box(Modifier.padding(start = (depth * 16).dp)) { content() }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = GvColors.TextMuted,
        modifier = Modifier.padding(top = LocalSpacing.current.md),
    )
}

@Composable
private fun BudgetTiles(data: BudgetMonth) {
    val spacing = LocalSpacing.current
    val balance = budgetBalance(data)
    val off = offBudget(data)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile("Spent", formatMoney(data.expense.actual), TileTone.NEG, Modifier.weight(1f), sub = listOf("of ${formatMoney(data.expense.budgeted)}"))
            KpiTile("Earned", formatMoney(data.income.actual), TileTone.POS, Modifier.weight(1f), sub = listOf("of ${formatMoney(data.income.budgeted)}"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile(
                "Balance",
                signed(balance),
                toneOf(balance),
                Modifier.weight(1f),
                sub = listOf("of ${signed(data.planned_balance.toDoubleOrNull() ?: 0.0)} planned"),
            )
            KpiTile(
                "Off budget",
                formatMoney(off),
                if (off > 0) TileTone.NEG else TileTone.NEUTRAL,
                Modifier.weight(1f),
                sub = listOf(
                    "${formatMoney(data.expense.unbudgeted)} outside budgets",
                    "${formatMoney(data.expense.overspent)} overspent",
                ),
            )
        }
    }
}

@Composable
private fun BudgetRow(
    item: BudgetItem,
    pace: Double?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val barColor = when (item.status) {
        "over" -> GvColors.Danger
        "warning" -> GvColors.Warning
        "met" -> GvColors.Success
        else -> GvColors.Primary
    }
    Column(
        modifier = Modifier
            .padding(start = (item.depth * 16).dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, if (expanded) GvColors.Primary.copy(alpha = 0.5f) else GvColors.BorderLight, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = GvColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text, modifier = Modifier.weight(1f), maxLines = 1)
            Text(formatMoney(item.actual), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text, fontWeight = FontWeight.SemiBold)
            Text("/ ${formatMoney(item.budget)}", style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
            Text(budgetPercentLabel(item), style = MaterialTheme.typography.labelMedium, color = barColor, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit budget", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        ProgressBar(fill = budgetFill(item), color = barColor, pace = pace?.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                budgetNote(item),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.status == "over") GvColors.Danger else GvColors.TextMuted,
            )
            Text(budgetSince(item), style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
        }
    }
}

/** The bar, with a tick where the period has got to (only while it is running). */
@Composable
private fun ProgressBar(fill: Float, color: Color, pace: Float?) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(GvColors.Bg))
        if (fill > 0f) Box(Modifier.fillMaxWidth(fill).fillMaxSize().clip(CircleShape).background(color))
        if (pace != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * pace - 1.dp)
                    .width(2.dp)
                    .fillMaxSize()
                    .background(GvColors.Text.copy(alpha = 0.7f)),
            )
        }
    }
}
