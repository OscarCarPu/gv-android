package com.gv.app.ui.money

import com.gv.app.ui.common.DayDivider
import com.gv.app.ui.common.EmptyHint
import com.gv.app.ui.common.ShowMore
import com.gv.app.ui.common.SmallButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.gv.app.data.repository.MoneyData
import com.gv.app.domain.model.OverviewTransaction
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Transaction
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * The web's Summary card: the six tiles, then the transactions — the last 30 days, or one
 * account's whole history when it is picked — grouped by day and folded, with "N more".
 * "+ Transaction" is disabled until there is an account to put one on.
 */
@Composable
internal fun OverviewTab(
    data: MoneyData,
    list: TxListState,
    onNewTransaction: () -> Unit,
    onEditTx: (OverviewTransaction) -> Unit,
    onDeleteTx: (OverviewTransaction) -> Unit,
    onSelectAccount: (Int?) -> Unit,
    onShowMore: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val todayKey = LocalDate.now().toString()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "summary-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Summary", style = MaterialTheme.typography.titleMedium, color = GvColors.Text, modifier = Modifier.weight(1f))
                SmallButton("Transaction", onNewTransaction, enabled = data.accounts.isNotEmpty(), icon = Icons.Filled.Add)
            }
        }
        item(key = "kpis") { KpiGrid(data) }

        item(key = "list-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (list.filtering) "Account history" else "Recent transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = GvColors.Text,
                    modifier = Modifier.weight(1f),
                )
                AccountFilter(data.accounts, list.filteringAccountId, onSelectAccount)
            }
        }

        when {
            list.loading && list.total == 0 -> item(key = "tx-loading") { EmptyHint("Loading…") }
            list.total == 0 -> item(key = "tx-empty") {
                EmptyHint(if (list.filtering) "No transactions for this account" else "No transactions in the last 30 days")
            }
            else -> {
                list.visible.forEachIndexed { i, tx ->
                    val key = tx.occurred_at.take(10)
                    val prevKey = list.visible.getOrNull(i - 1)?.occurred_at?.take(10)
                    if (i == 0 || key != prevKey) {
                        item(key = "day-$i-$key") { DayDivider(label = formatDayLabel(key), highlight = key == todayKey) }
                    }
                    item(key = "tx-${tx.id}") { TxRow(tx, onClick = { onEditTx(tx) }, onDelete = { onDeleteTx(tx) }) }
                }
                if (list.hasMore) item(key = "tx-more") { ShowMore(list.remaining, onShowMore) }
            }
        }
    }
}

/** "All accounts (recent)" or one account, as the web's filter select. */
@Composable
private fun AccountFilter(accounts: List<Account>, selected: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = accounts.firstOrNull { it.id == selected }?.name ?: "All accounts"
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected != null) GvColors.Primary.copy(alpha = 0.18f) else GvColors.BgLight)
                .border(1.dp, if (selected != null) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(16.dp))
                .clickable { open = true }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected != null) GvColors.Primary else GvColors.TextMuted, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Filter by account", tint = GvColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = GvColors.BgLight) {
            DropdownMenuItem(text = { Text("All accounts (recent)", color = GvColors.TextMuted) }, onClick = { onSelect(null); open = false })
            accounts.forEach { a ->
                DropdownMenuItem(text = { Text(a.name, color = GvColors.Text) }, onClick = { onSelect(a.id); open = false })
            }
        }
    }
}

/**
 * The web's six tiles, in its order and with its words: Total accounts, Monthly income,
 * Monthly expenses, Monthly balance, Savings, % vs prev month.
 */
@Composable
private fun KpiGrid(data: MoneyData) {
    val spacing = LocalSpacing.current
    val overview = data.overview
    val kpis = deriveOverviewKpis(overview)

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile("Total accounts", formatMoney(overview.accounts_total), TileTone.NEUTRAL, Modifier.weight(1f))
            KpiTile("Monthly income", "+" + formatMoney(overview.month.income), TileTone.POS, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile("Monthly expenses", "−" + formatMoney(overview.month.expense), TileTone.NEG, Modifier.weight(1f))
            KpiTile("Monthly balance", kpis.balancePrefix + formatMoney(kotlin.math.abs(kpis.balance)), toneOf(kpis.balance), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile("Savings", formatPercent(kpis.savingsRate), toneOf(kpis.savingsRate), Modifier.weight(1f))
            KpiTile(
                "% vs prev month",
                if (kpis.hasPrevBalance) (if (kpis.balanceChangePct >= 0) "+" else "−") + formatPercent(kotlin.math.abs(kpis.balanceChangePct)) else "—",
                if (kpis.hasPrevBalance) toneOf(kpis.balanceChangePct) else TileTone.NEUTRAL,
                Modifier.weight(1f),
            )
        }
    }
}

// ---------- Formatting helpers ----------

private val DayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)

internal fun formatDayLabel(yyyyMmDd: String): String {
    return try {
        val d = LocalDate.parse(yyyyMmDd)
        val today = LocalDate.now()
        when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> d.format(DayFormatter)
        }
    } catch (_: Exception) {
        yyyyMmDd
    }
}
