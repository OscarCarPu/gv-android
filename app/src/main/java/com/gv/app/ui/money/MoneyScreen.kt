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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.OverviewTransaction
import com.gv.app.domain.model.Transaction
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

private enum class MoneyTab(val label: String) {
    OVERVIEW("Overview"),
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
}

private sealed class ActiveSheet {
    data class NewTransaction(val editing: Transaction? = null) : ActiveSheet()
    data class EditAccount(val account: Account?) : ActiveSheet()
    data class EditCategory(val category: Category?) : ActiveSheet()
}

private sealed class PendingDelete {
    data class Tx(val id: Int, val label: String) : PendingDelete()
    data class Acc(val account: Account) : PendingDelete()
    data class Cat(val category: Category) : PendingDelete()
}

@Composable
fun MoneyScreen(vm: MoneyViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(MoneyTab.OVERVIEW) }
    var sheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    // Swipe left/right to change part, kept in sync with the tab bar.
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { MoneyTab.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> tab = MoneyTab.entries[page] }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal) pagerState.animateScrollToPage(tab.ordinal)
    }

    LaunchedEffect(vm) {
        vm.toast.collect { message -> snackbar.showSnackbar(message) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabBar(selected = tab, onSelect = { tab = it })

            when (val s = state) {
                is MoneyUiState.Loading -> CenteredLoader()
                is MoneyUiState.Error -> ErrorState(s.message, onRetry = vm::refresh)
                is MoneyUiState.Loaded -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { it },
                ) { page ->
                    when (MoneyTab.entries[page]) {
                        MoneyTab.OVERVIEW -> OverviewView(
                            overview = s.data.overview,
                            onEditTx = { ovTx ->
                                vm.loadTransaction(ovTx.id) { full ->
                                    if (full != null) sheet = ActiveSheet.NewTransaction(full)
                                }
                            },
                            onDeleteTx = { ovTx ->
                                pendingDelete = PendingDelete.Tx(
                                    id = ovTx.id,
                                    label = ovTx.description?.takeIf { it.isNotBlank() }
                                        ?: ovTx.category_name
                                        ?: "this movement",
                                )
                            },
                        )
                        MoneyTab.ACCOUNTS -> AccountsView(
                            accounts = s.data.accounts,
                            onEdit = { sheet = ActiveSheet.EditAccount(it) },
                            onDelete = { pendingDelete = PendingDelete.Acc(it) },
                        )
                        MoneyTab.CATEGORIES -> CategoriesView(
                            categories = s.data.categories,
                            onEdit = { sheet = ActiveSheet.EditCategory(it) },
                            onDelete = { pendingDelete = PendingDelete.Cat(it) },
                        )
                    }
                }
            }
        }

        if (state is MoneyUiState.Loaded) {
            FloatingActionButton(
                onClick = {
                    sheet = when (tab) {
                        MoneyTab.OVERVIEW -> ActiveSheet.NewTransaction()
                        MoneyTab.ACCOUNTS -> ActiveSheet.EditAccount(null)
                        MoneyTab.CATEGORIES -> ActiveSheet.EditCategory(null)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 0.dp),
                containerColor = GvColors.Primary,
                contentColor = GvColors.Text,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = GvColors.Surface,
                contentColor = GvColors.Text,
            )
        }
    }

    // --- Sheets ---
    val data = (state as? MoneyUiState.Loaded)?.data
    when (val s = sheet) {
        is ActiveSheet.NewTransaction -> if (data != null) {
            if (data.accounts.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { sheet = null },
                    containerColor = GvColors.BgLight,
                    title = { Text("No accounts", color = GvColors.Text) },
                    text = {
                        Text(
                            "Create an account first to record transactions.",
                            color = GvColors.TextMuted,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            sheet = ActiveSheet.EditAccount(null)
                        }) {
                            Text("New account", color = GvColors.Primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { sheet = null }) {
                            Text("Cancel", color = GvColors.TextMuted)
                        }
                    },
                )
            } else {
                TransactionFormSheet(
                    transaction = s.editing,
                    accounts = data.accounts,
                    categories = data.categories,
                    onDismiss = { sheet = null },
                    onSave = { id, req ->
                        vm.saveTransaction(id, req) { ok -> if (ok) sheet = null }
                    },
                )
            }
        }
        is ActiveSheet.EditAccount -> AccountFormSheet(
            initialName = s.account?.name,
            isEdit = s.account != null,
            onDismiss = { sheet = null },
            onSave = { name ->
                vm.saveAccount(s.account?.id, name) { ok -> if (ok) sheet = null }
            },
        )
        is ActiveSheet.EditCategory -> if (data != null) {
            CategoryFormSheet(
                category = s.category,
                allCategories = data.categories,
                onDismiss = { sheet = null },
                onSave = { name, type, parentId ->
                    vm.saveCategory(s.category?.id, name, type, parentId) { ok ->
                        if (ok) sheet = null
                    }
                },
            )
        }
        null -> Unit
    }

    // --- Delete confirm ---
    when (val p = pendingDelete) {
        is PendingDelete.Tx -> ConfirmDeleteDialog(
            title = "Delete movement?",
            message = "\"${p.label}\" will be removed. This cannot be undone.",
            onConfirm = { vm.deleteTransaction(p.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
        is PendingDelete.Acc -> ConfirmDeleteDialog(
            title = "Delete account?",
            message = "\"${p.account.name}\" will be removed. Accounts with transactions cannot be deleted.",
            onConfirm = { vm.deleteAccount(p.account.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
        is PendingDelete.Cat -> ConfirmDeleteDialog(
            title = "Delete category?",
            message = "\"${p.category.name}\" will be removed. Categories in use cannot be deleted.",
            onConfirm = { vm.deleteCategory(p.category.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
        null -> Unit
    }
}

@Composable
private fun TabBar(selected: MoneyTab, onSelect: (MoneyTab) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.BgLight)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        MoneyTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) GvColors.Primary else GvColors.BorderLight,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) GvColors.Primary else GvColors.TextMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------- Overview ----------

@Composable
private fun OverviewView(
    overview: Overview,
    onEditTx: (OverviewTransaction) -> Unit,
    onDeleteTx: (OverviewTransaction) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        KpiGrid(overview)

        Text(
            text = "Recent movements",
            style = MaterialTheme.typography.titleMedium,
            color = GvColors.Text,
        )

        if (overview.recent_transactions.isEmpty()) {
            EmptyHint("No movements in the last 30 days")
        } else {
            val todayKey = LocalDate.now().toString()
            var lastKey = ""
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                overview.recent_transactions.forEach { tx ->
                    val key = tx.occurred_at.substring(0, 10.coerceAtMost(tx.occurred_at.length))
                    if (key != lastKey) {
                        DayDivider(label = formatDayLabel(key), highlight = key == todayKey)
                        lastKey = key
                    }
                    TxRow(tx, onClick = { onEditTx(tx) }, onDelete = { onDeleteTx(tx) })
                }
            }
        }
    }
}

@Composable
private fun KpiGrid(overview: Overview) {
    val spacing = LocalSpacing.current
    val balanceNum = overview.month.balance.toDoubleOrNull() ?: 0.0
    val incomeNum = overview.month.income.toDoubleOrNull() ?: 0.0
    val savings = if (incomeNum > 0) (balanceNum / incomeNum) * 100 else 0.0
    val prevBalanceNum = overview.previous_month.balance.toDoubleOrNull() ?: 0.0
    val hasPrev = prevBalanceNum != 0.0
    val changePct = if (hasPrev) ((balanceNum - prevBalanceNum) / abs(prevBalanceNum)) * 100 else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile(
                title = "Total accounts",
                value = formatMoney(overview.accounts_total),
                tone = TileTone.NEUTRAL,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                title = "Balance (month)",
                value = signed(balanceNum),
                tone = toneOf(balanceNum),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile(
                title = "Income (month)",
                value = "+" + formatMoney(overview.month.income),
                tone = TileTone.POS,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                title = "Expense (month)",
                value = "−" + formatMoney(overview.month.expense),
                tone = TileTone.NEG,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            KpiTile(
                title = "Savings rate",
                value = String.format(Locale.UK, "%.1f%%", savings),
                tone = toneOf(savings),
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                title = "vs previous",
                value = if (hasPrev) String.format(
                    Locale.UK,
                    "%s%.1f%%",
                    if (changePct >= 0) "+" else "−",
                    abs(changePct),
                ) else "—",
                tone = if (hasPrev) toneOf(changePct) else TileTone.NEUTRAL,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class TileTone { POS, NEG, NEUTRAL }

private fun toneOf(v: Double): TileTone =
    if (v > 0) TileTone.POS else if (v < 0) TileTone.NEG else TileTone.NEUTRAL

private fun signed(v: Double): String {
    val abs = formatMoney(kotlin.math.abs(v))
    return when {
        v > 0 -> "+$abs"
        v < 0 -> "−$abs"
        else -> abs
    }
}

@Composable
private fun KpiTile(title: String, value: String, tone: TileTone, modifier: Modifier = Modifier) {
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
private fun DayDivider(label: String, highlight: Boolean) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.md, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(GvColors.BorderLight)
                .size(width = 0.dp, height = 1.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = spacing.md),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .background(GvColors.BorderLight)
                .size(width = 0.dp, height = 1.dp),
        )
    }
}

@Composable
private fun TxRow(
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
private fun TypeBadge(type: String) {
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

// ---------- Accounts ----------

@Composable
private fun AccountsView(
    accounts: List<Account>,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    val spacing = LocalSpacing.current
    if (accounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(spacing.xxl), contentAlignment = Alignment.Center) {
            Text(
                text = "No accounts yet. Tap + to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        items(items = accounts, key = { it.id }) { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GvColors.BgLight)
                    .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
                    .padding(horizontal = spacing.lg, vertical = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = GvColors.Text,
                    )
                    Text(
                        text = formatMoney(account.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = if ((account.total.toDoubleOrNull() ?: 0.0) < 0)
                            GvColors.Danger else GvColors.TextMuted,
                    )
                }
                IconButton(onClick = { onEdit(account) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = GvColors.TextMuted)
                }
                IconButton(onClick = { onDelete(account) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GvColors.TextMuted)
                }
            }
        }
    }
}

// ---------- Categories ----------

@Composable
private fun CategoriesView(
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    val spacing = LocalSpacing.current
    if (categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(spacing.xxl), contentAlignment = Alignment.Center) {
            Text(
                text = "No categories yet. Tap + to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
            )
        }
        return
    }
    val groups = listOf(
        Triple("income", "Income", GvColors.Success),
        Triple("expense", "Expense", GvColors.Danger),
        Triple("transfer", "Transfer", GvColors.Secondary),
    )

    val collapsed = rememberSaveable(saver = collapsedSetSaver()) { mutableStateOf(emptySet<Int>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        groups.forEach { (type, label, accent) ->
            val rows = buildCategoryTreeRows(categories.filter { it.type == type })
            val visibleRows = filterCollapsed(rows, collapsed.value)
            if (rows.isNotEmpty()) {
                item(key = "header-$type") {
                    GroupHeader(label = label, accent = accent, count = rows.size)
                }
                items(items = visibleRows, key = { "cat-${it.category.id}" }) { row ->
                    CategoryTreeNode(
                        row = row,
                        accent = accent,
                        isCollapsed = row.category.id in collapsed.value,
                        onToggle = {
                            collapsed.value = collapsed.value.toMutableSet().apply {
                                if (!add(row.category.id)) remove(row.category.id)
                            }
                        },
                        onEdit = { onEdit(row.category) },
                        onDelete = { onDelete(row.category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, accent: Color, count: Int) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.lg, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
    }
}

@Composable
private fun CategoryTreeNode(
    row: CategoryTreeRow,
    accent: Color,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val rowBg = if (row.depth == 0) GvColors.BgLight else GvColors.BgLight.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .padding(vertical = spacing.sm, horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TreeConnectors(
            depth = row.depth,
            isLast = row.isLast,
            ancestorHasMore = row.ancestorHasMore,
            accent = accent,
        )

        if (row.hasChildren) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (isCollapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(modifier = Modifier.size(28.dp))
        }

        Text(
            text = row.category.name,
            style = if (row.depth == 0) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodySmall,
            color = GvColors.Text,
            fontWeight = if (row.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = GvColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = GvColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TreeConnectors(
    depth: Int,
    isLast: Boolean,
    ancestorHasMore: BooleanArray,
    accent: Color,
) {
    if (depth == 0) return
    val columnWidth = 18.dp
    val lineColor = accent.copy(alpha = 0.35f)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(width = columnWidth * depth, height = 40.dp),
    ) {
        val colPx = columnWidth.toPx()
        val midY = size.height / 2f
        val strokeWidth = 1.5f.dp.toPx()
        // vertical guide lines for ancestors
        for (i in 0 until depth - 1) {
            if (i < ancestorHasMore.size && ancestorHasMore[i]) {
                val x = colPx * (i + 0.5f)
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = strokeWidth,
                )
            }
        }
        // branch at this row's column
        val xBranch = colPx * (depth - 1 + 0.5f)
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(xBranch, 0f),
            end = androidx.compose.ui.geometry.Offset(xBranch, if (isLast) midY else size.height),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(xBranch, midY),
            end = androidx.compose.ui.geometry.Offset(colPx * depth, midY),
            strokeWidth = strokeWidth,
        )
    }
}

private fun filterCollapsed(
    rows: List<CategoryTreeRow>,
    collapsed: Set<Int>,
): List<CategoryTreeRow> {
    if (collapsed.isEmpty()) return rows
    val out = mutableListOf<CategoryTreeRow>()
    var hiddenAtDepth: Int? = null
    for (row in rows) {
        val hidden = hiddenAtDepth
        if (hidden != null && row.depth > hidden) continue
        hiddenAtDepth = null
        out.add(row)
        if (row.category.id in collapsed && row.hasChildren) {
            hiddenAtDepth = row.depth
        }
    }
    return out
}

private fun collapsedSetSaver(): androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<Set<Int>>, ArrayList<Int>> =
    androidx.compose.runtime.saveable.Saver(
        save = { ArrayList(it.value) },
        restore = { androidx.compose.runtime.mutableStateOf(it.toSet()) },
    )

// ---------- Shared ----------

@Composable
private fun CenteredLoader() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GvColors.Primary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )
        OutlinedButton(onClick = onRetry) {
            Text("Retry", color = GvColors.Primary)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GvColors.BgLight,
        title = { Text(title, color = GvColors.Text) },
        text = { Text(message, color = GvColors.TextMuted) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Danger,
                    contentColor = GvColors.Text,
                ),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GvColors.TextMuted)
            }
        },
    )
}

// ---------- Formatting helpers ----------

private val DayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)

private fun formatDayLabel(yyyyMmDd: String): String {
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

