package com.gv.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.gv.app.domain.model.Transaction
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

private enum class MoneyTab(val label: String) {
    OVERVIEW("Overview"),
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
}

private sealed class ActiveSheet {
    data class Transaction(val editing: com.gv.app.domain.model.Transaction? = null) : ActiveSheet()
    data class EditAccount(val account: Account?) : ActiveSheet()
    data class EditCategory(val category: Category?) : ActiveSheet()
}

/**
 * The Money screen: Overview | Accounts | Categories as swipeable pages, each with the web's
 * header button (**+ Transaction**, **New**) instead of a floating one. Deletes are immediate and
 * the only dialog left is the "no accounts yet" nudge when there is nowhere to put a transaction.
 */
@Composable
fun MoneyScreen(vm: MoneyViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val list by vm.transactions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(MoneyTab.OVERVIEW) }
    var sheet by remember { mutableStateOf<ActiveSheet?>(null) }

    // Swipe left/right to change part, kept in sync with the tab bar.
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { MoneyTab.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> tab = MoneyTab.entries[page] }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal) pagerState.animateScrollToPage(tab.ordinal)
    }
    LaunchedEffect(vm) { vm.toast.collect { message -> snackbar.showSnackbar(message) } }

    Box(modifier = Modifier.fillMaxSize().background(GvColors.Bg)) {
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
                        MoneyTab.OVERVIEW -> OverviewTab(
                            data = s.data,
                            list = list,
                            onNewTransaction = { sheet = ActiveSheet.Transaction() },
                            onEditTx = { row ->
                                vm.loadTransaction(row.id) { full -> if (full != null) sheet = ActiveSheet.Transaction(full) }
                            },
                            onDeleteTx = { row -> vm.deleteTransaction(row.id) },
                            onSelectAccount = vm::selectAccount,
                            onShowMore = vm::showMore,
                        )
                        MoneyTab.ACCOUNTS -> AccountsTab(
                            accounts = s.data.accounts,
                            onNew = { sheet = ActiveSheet.EditAccount(null) },
                            onEdit = { sheet = ActiveSheet.EditAccount(it) },
                            onDelete = vm::deleteAccount,
                        )
                        MoneyTab.CATEGORIES -> CategoriesTab(
                            categories = s.data.categories,
                            onNew = { sheet = ActiveSheet.EditCategory(null) },
                            onEdit = { sheet = ActiveSheet.EditCategory(it) },
                            onDelete = vm::deleteCategory,
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
            Snackbar(snackbarData = data, containerColor = GvColors.Surface, contentColor = GvColors.Text)
        }
    }

    val data = (state as? MoneyUiState.Loaded)?.data
    when (val s = sheet) {
        is ActiveSheet.Transaction -> if (data != null) {
            if (data.accounts.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { sheet = null },
                    containerColor = GvColors.BgLight,
                    title = { Text("No accounts", color = GvColors.Text) },
                    text = { Text("Create an account first to record transactions.", color = GvColors.TextMuted) },
                    confirmButton = {
                        TextButton(onClick = { sheet = ActiveSheet.EditAccount(null) }) { Text("New account", color = GvColors.Primary) }
                    },
                    dismissButton = { TextButton(onClick = { sheet = null }) { Text("Cancel", color = GvColors.TextMuted) } },
                )
            } else {
                TransactionFormSheet(
                    transaction = s.editing,
                    accounts = data.accounts,
                    categories = data.categories,
                    onDismiss = { sheet = null },
                    onSave = { form, occurredAt ->
                        vm.saveTransaction(s.editing?.id, form, occurredAt) { ok -> if (ok) sheet = null }
                    },
                )
            }
        }
        is ActiveSheet.EditAccount -> AccountFormSheet(
            initialName = s.account?.name,
            isEdit = s.account != null,
            onDismiss = { sheet = null },
            onSave = { name -> vm.saveAccount(s.account?.id, name) { ok -> if (ok) sheet = null } },
        )
        is ActiveSheet.EditCategory -> if (data != null) {
            CategoryFormSheet(
                category = s.category,
                allCategories = data.categories,
                onDismiss = { sheet = null },
                onSave = { name, type, parentId ->
                    vm.saveCategory(s.category?.id, name, type, parentId) { ok -> if (ok) sheet = null }
                },
            )
        }
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
