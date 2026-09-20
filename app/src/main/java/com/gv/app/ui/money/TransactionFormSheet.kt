package com.gv.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.Transaction
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDateTime

// ---------- Transaction ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    transaction: Transaction?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    /** The checked form, and when it happened as a wall-clock stamp (see [wallClockToIso]). */
    onSave: (TransactionFormCheck.Ok, occurredAtIso: String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(transaction?.type ?: "expense") }
    var amount by remember { mutableStateOf(transaction?.amount ?: "") }
    var accountId by remember { mutableStateOf(transaction?.account_id ?: accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf(transaction?.to_account_id) }
    var categoryId by remember { mutableStateOf(transaction?.category_id) }
    var description by remember { mutableStateOf(transaction?.description ?: "") }
    var occurredAt by remember {
        mutableStateOf(
            transaction?.occurred_at?.let { isoToWallClock(it) } ?: LocalDateTime.now(),
        )
    }
    var amountError by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var toAccountError by remember { mutableStateOf(false) }

    LaunchedEffect(type) {
        if (type != "transfer") toAccountId = null
    }

    val categoryOptions = remember(categories, type) {
        buildCategoryOptions(categories.filter { it.type == type })
    }
    LaunchedEffect(categoryOptions) {
        if (categoryId != null && categoryOptions.none { it.id == categoryId }) {
            categoryId = null
        }
    }

    val toAccountOptions = remember(accounts, accountId) {
        accounts.filter { it.id != accountId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = if (transaction != null) "Edit transaction" else "New transaction",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            TypeSelector(type = type, onChange = { type = it })

            GvTextField(
                label = "Amount",
                value = amount,
                onValueChange = { amount = it; amountError = false },
                error = amountError,
                keyboardType = KeyboardType.Decimal,
            )

            DateField(
                label = "Date",
                value = occurredAt,
                onChange = { occurredAt = it },
            )

            DropdownField(
                label = if (type == "transfer") "From account" else "Account",
                selectedLabel = accounts.firstOrNull { it.id == accountId }?.name ?: "Select account",
                error = accountError,
                items = accounts.map { it.id to it.name },
                onSelect = { id -> accountId = id; accountError = false },
            )

            if (type == "transfer") {
                DropdownField(
                    label = "To account",
                    selectedLabel = toAccountOptions.firstOrNull { it.id == toAccountId }?.name
                        ?: "Select destination",
                    error = toAccountError,
                    items = toAccountOptions.map { it.id to it.name },
                    onSelect = { id -> toAccountId = id; toAccountError = false },
                )
            }

            DropdownField(
                label = "Category",
                selectedLabel = categoryOptions.firstOrNull { it.id == categoryId }?.name
                    ?: "Select category",
                error = categoryError,
                items = categoryOptions.map { it.id to it.label },
                onSelect = { id -> categoryId = id; categoryError = false },
            )

            GvTextField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                singleLine = false,
            )

            Button(
                onClick = {
                    when (val check = checkTransactionForm(type, amount, accountId, toAccountId, categoryId, description)) {
                        is TransactionFormCheck.Invalid -> {
                            amountError = check.amountError
                            accountError = check.accountError
                            categoryError = check.categoryError
                            toAccountError = check.toAccountError
                        }
                        is TransactionFormCheck.Ok -> onSave(check, wallClockToIso(occurredAt))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (transaction != null) "Save" else "Create")
            }
        }
    }
}
