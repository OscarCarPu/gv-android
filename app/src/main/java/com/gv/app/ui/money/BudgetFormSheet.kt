package com.gv.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.gv.app.domain.model.BudgetItem
import com.gv.app.domain.model.BudgetMonth
import com.gv.app.domain.model.Category
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/**
 * Create or edit a budget, as the web's `BudgetFormSheet`: category (expense and income only),
 * period, amount with the suggested one a tap away, and how far the change reaches — this period
 * and every later one, or this one only. Editing fixes the category and period (they are the
 * budget's identity) and adds **Remove**, which takes the same scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetFormSheet(
    item: BudgetItem?,
    categories: List<Category>,
    budgets: BudgetMonth,
    onDismiss: () -> Unit,
    onSave: (categoryId: Int, amount: String, scope: String, period: String) -> Unit,
    onRemove: (scope: String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editing = item != null

    var categoryId by remember { mutableStateOf(item?.category_id) }
    var period by remember { mutableStateOf(item?.period ?: "monthly") }
    var amount by remember { mutableStateOf(item?.let { budgetAmountText(it.budget) } ?: "") }
    var scope by remember { mutableStateOf("forward") }
    var categoryError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val budgetable = remember(categories) { budgetableCategories(categories) }
    val options = remember(budgetable) {
        listOf("expense" to "Expenses", "income" to "Income").flatMap { (type, _) ->
            buildCategoryOptions(budgetable.filter { it.type == type })
        }
    }
    val suggestion = budgetSuggestion(budgets, categoryId, period)
    val periodWord = if (period == "yearly") "year" else "month"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                if (editing) "Edit budget" else "New budget",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            if (editing) {
                Text(item!!.name, style = MaterialTheme.typography.bodyLarge, color = GvColors.Text)
            } else {
                DropdownField(
                    label = "Category",
                    selectedLabel = options.firstOrNull { it.id == categoryId }?.name ?: "Select category",
                    error = categoryError,
                    items = options.map { it.id to it.label },
                    onSelect = { categoryId = it; categoryError = false },
                )
            }

            if (!editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TypeChip("Monthly", "monthly", period, GvColors.Primary, { period = it }, Modifier.weight(1f))
                    TypeChip("Yearly", "yearly", period, GvColors.Primary, { period = it }, Modifier.weight(1f))
                }
            }

            GvTextField(
                label = if (period == "yearly") "Amount per year" else "Amount per month",
                value = amount,
                onValueChange = { amount = it; amountError = false },
                error = amountError,
                keyboardType = KeyboardType.Decimal,
            )
            if (suggestion != null) {
                TextButton(onClick = { amount = budgetAmountText(suggestion); amountError = false }) {
                    Text(
                        (if (period == "yearly") "Last year: " else "3-month average: ") + formatMoney(suggestion),
                        color = GvColors.Primary,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Applies to", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TypeChip("This $periodWord onward", "forward", scope, GvColors.Primary, { scope = it }, Modifier.weight(1f))
                    TypeChip("This $periodWord only", "once", scope, GvColors.Primary, { scope = it }, Modifier.weight(1f))
                }
            }

            Button(
                onClick = {
                    when (val check = checkBudgetForm(categoryId, amount)) {
                        is BudgetFormCheck.Invalid -> {
                            categoryError = check.categoryError
                            amountError = check.amountError
                        }
                        is BudgetFormCheck.Ok -> onSave(check.categoryId, check.amount, scope, period)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing) "Save" else "Create")
            }
            if (editing) {
                OutlinedButton(onClick = { onRemove(scope) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove", color = GvColors.Danger)
                }
            }
        }
    }
}
