package com.gv.app.domain.model

data class Account(
    val id: Int,
    val name: String,
    val total: String,
    val created_at: String,
)

data class Category(
    val id: Int,
    val name: String,
    val parent_id: Int?,
    val type: String,
    val created_at: String,
)

data class Transaction(
    val id: Int,
    val type: String,
    val amount: String,
    val account_id: Int,
    val to_account_id: Int?,
    val category_id: Int?,
    val description: String?,
    val occurred_at: String,
    val created_at: String,
)

data class OverviewTransaction(
    val id: Int,
    val type: String,
    val amount: String,
    val account_name: String,
    val to_account_name: String?,
    val category_name: String?,
    val description: String?,
    val occurred_at: String,
)

data class OverviewMonth(
    val income: String,
    val expense: String,
    val balance: String,
)

data class Overview(
    val accounts_total: String,
    val month: OverviewMonth,
    val previous_month: OverviewMonth,
    val recent_transactions: List<OverviewTransaction>,
)

data class CreateAccountRequest(val name: String)
data class UpdateAccountRequest(val name: String)

data class CreateCategoryRequest(
    val name: String,
    val parent_id: Int?,
    val type: String,
)

data class UpdateCategoryRequest(
    val name: String,
    val parent_id: Int?,
    val type: String,
)

data class CreateTransactionRequest(
    val type: String,
    val amount: String,
    val account_id: Int,
    val to_account_id: Int?,
    val category_id: Int?,
    val description: String?,
    val occurred_at: String?,
)

data class UpdateTransactionRequest(
    val type: String,
    val amount: String,
    val account_id: Int,
    val to_account_id: Int?,
    val category_id: Int?,
    val description: String?,
    val occurred_at: String,
)

// --- Budgets ---

data class BudgetTotals(
    val budgeted: String,
    val actual: String,
    /** Actual in categories no budget covers (incl. uncategorized). */
    val unbudgeted: String,
    /** Expense beyond the budgets, nested budgets counted once (0 for income). */
    val overspent: String,
)

data class BudgetItem(
    val category_id: Int,
    /** `monthly` or `yearly`. */
    val period: String,
    val name: String,
    val parent_id: Int?,
    /** `income` or `expense`. */
    val type: String,
    /** Number of budgeted ancestors of the same type, for nesting. */
    val depth: Int,
    val budget: String,
    val actual: String,
    val remaining: String,
    val progress: Double,
    /** `ok`, `warning`, `over` (expenses); `pending`, `met` (income). */
    val status: String,
    /** Period the value in effect started: `YYYY-MM` (monthly) or `YYYY` (yearly). */
    val since: String,
)

data class BudgetAmount(val category_id: Int, val amount: String)

data class BudgetYear(
    val year: String,
    val year_progress: Double,
    val expense: BudgetTotals,
    val income: BudgetTotals,
    // Go serializes an empty slice as null.
    val items: List<BudgetItem>?,
)

data class BudgetMonth(
    val month: String,
    val month_progress: Double,
    val expense: BudgetTotals,
    val income: BudgetTotals,
    val items: List<BudgetItem>?,
    val planned_balance: String,
    val yearly: BudgetYear,
    /** Average of the last 3 complete months per category, to suggest a monthly amount. */
    val averages: List<BudgetAmount>?,
    /** Last calendar year's total per category, to suggest a yearly amount. */
    val previous_year: List<BudgetAmount>?,
)

data class SetBudgetRequest(
    val month: String,
    val amount: String,
    /** `forward` or `once`. */
    val scope: String,
    val period: String,
)
