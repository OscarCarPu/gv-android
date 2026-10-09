package com.gv.app.ui.money

import com.gv.app.domain.model.BudgetItem
import com.gv.app.domain.model.BudgetMonth
import com.gv.app.domain.model.Category
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Budget rules ported from gv-web (`budgets.svelte.ts`, `budgetForm.svelte.ts`,
 * `utils/budgetMonth.ts`, `BudgetRow.svelte`) so both apps read a budget the same way.
 *
 * The API owns the maths — `actual`, `remaining`, `progress`, `status`, roll-ups, the planned
 * balance. This file only chooses what to show and checks what is typed.
 */

// ---------- Months ----------

private val MonthRe = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

fun isBudgetMonth(value: String?): Boolean = value != null && MonthRe.matches(value)

/** `YYYY-MM` of today in the phone's zone. */
fun currentBudgetMonth(today: LocalDate = LocalDate.now()): String = YearMonth.from(today).toString()

fun shiftBudgetMonth(ym: String, delta: Int): String = YearMonth.parse(ym).plusMonths(delta.toLong()).toString()

/** `2026-09` → `September 2026` (or `Sep 2026` when [short]), English as on the web. */
fun formatBudgetMonth(ym: String, short: Boolean = false): String =
    YearMonth.parse(ym).format(DateTimeFormatter.ofPattern(if (short) "MMM yyyy" else "MMMM yyyy", Locale.US))

// ---------- Reading a month ----------

/** The elapsed-share marker only means something while the period is running. */
fun runningPace(progress: Double): Double? = if (progress > 0.0 && progress < 1.0) progress else null

data class BudgetGroup(val type: String, val label: String, val items: List<BudgetItem>)

fun groupBudgetsByType(items: List<BudgetItem>?): List<BudgetGroup> {
    val all = items.orEmpty()
    return listOf(
        BudgetGroup("expense", "Expenses", all.filter { it.type == "expense" }),
        BudgetGroup("income", "Income", all.filter { it.type == "income" }),
    )
}

private fun num(s: String): Double = s.toDoubleOrNull() ?: 0.0

/** Actual income − actual expenses so far this month. */
fun budgetBalance(m: BudgetMonth): Double = num(m.income.actual) - num(m.expense.actual)

/** Expenses outside the plan: spent in unbudgeted categories plus spent over budgets. */
fun offBudget(m: BudgetMonth): Double = num(m.expense.unbudgeted) + num(m.expense.overspent)

fun hasYearlyBudgets(m: BudgetMonth): Boolean = !m.yearly.items.isNullOrEmpty()

fun hasNoBudgets(m: BudgetMonth): Boolean = m.items.isNullOrEmpty() && !hasYearlyBudgets(m)

/** The row's one-line verdict: income speaks of what is left to earn, expenses of what is left to spend. */
fun budgetNote(item: BudgetItem): String {
    val remaining = num(item.remaining)
    val amount = formatMoney(kotlin.math.abs(remaining))
    return if (item.type == "income") {
        if (remaining > 0) "$amount to go" else "$amount above"
    } else {
        if (remaining >= 0) "$amount left" else "$amount over"
    }
}

/** Bar fill, capped at full: an overspent budget is a full red bar, not an overflowing one. */
fun budgetFill(item: BudgetItem): Float = item.progress.coerceIn(0.0, 1.0).toFloat()

fun budgetPercentLabel(item: BudgetItem): String =
    if (item.status == "met") "✓" else "${Math.round(item.progress * 100)}%"

fun budgetSince(item: BudgetItem): String =
    "since " + if (item.period == "yearly") item.since else formatBudgetMonth(item.since, short = true)

// ---------- The form ----------

/** Expense and income categories only: transfers cannot be budgeted. */
fun budgetableCategories(categories: List<Category>): List<Category> =
    categories.filter { it.type == "expense" || it.type == "income" }

/** The suggested amount for a category: a 3-month average when monthly, last year's total when yearly. */
fun budgetSuggestion(m: BudgetMonth, categoryId: Int?, period: String): String? {
    if (categoryId == null) return null
    val list = if (period == "yearly") m.previous_year else m.averages
    return list.orEmpty().firstOrNull { it.category_id == categoryId }?.amount
}

sealed class BudgetFormCheck {
    data class Ok(val categoryId: Int, val amount: String) : BudgetFormCheck()
    data class Invalid(val categoryError: Boolean, val amountError: Boolean) : BudgetFormCheck()
}

/** A category and an amount that is a number and not negative (`0` is a real budget). A decimal comma is accepted. */
fun checkBudgetForm(categoryId: Int?, amountText: String): BudgetFormCheck {
    val amount = amountText.trim().replace(',', '.').toDoubleOrNull()
    val amountError = amount == null || !amount.isFinite() || amount < 0
    if (categoryId == null || amountError) return BudgetFormCheck.Invalid(categoryId == null, amountError)
    return BudgetFormCheck.Ok(categoryId, String.format(Locale.ROOT, "%.2f", amount))
}

/** What an existing budget's amount looks like in the field: `350.00` → `350`, `12.50` → `12.5`. */
fun budgetAmountText(amount: String): String {
    val n = amount.toDoubleOrNull() ?: return amount
    return if (n == Math.floor(n)) n.toLong().toString() else n.toString()
}
