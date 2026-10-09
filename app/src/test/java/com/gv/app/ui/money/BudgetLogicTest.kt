package com.gv.app.ui.money

import com.gv.app.domain.model.BudgetAmount
import com.gv.app.domain.model.BudgetItem
import com.gv.app.domain.model.BudgetMonth
import com.gv.app.domain.model.BudgetTotals
import com.gv.app.domain.model.BudgetYear
import com.gv.app.domain.model.Category
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Budget readings that render plausibly while being wrong: a sign, a month edge, a null list. */
class BudgetLogicTest {

    private val nbsp = "\u00A0"

    private fun item(
        id: Int = 1,
        type: String = "expense",
        period: String = "monthly",
        budget: String = "100",
        actual: String = "50",
        remaining: String = "50",
        progress: Double = 0.5,
        status: String = "ok",
        since: String = "2026-01",
    ) = BudgetItem(id, period, "c$id", null, type, 0, budget, actual, remaining, progress, status, since)

    private fun totals(actual: String = "0", budgeted: String = "0", unbudgeted: String = "0", overspent: String = "0") =
        BudgetTotals(budgeted, actual, unbudgeted, overspent)

    private fun month(
        items: List<BudgetItem>? = emptyList(),
        yearly: List<BudgetItem>? = emptyList(),
        expense: BudgetTotals = totals(),
        income: BudgetTotals = totals(),
        averages: List<BudgetAmount>? = null,
        previousYear: List<BudgetAmount>? = null,
    ) = BudgetMonth(
        "2026-03", 0.5, expense, income, items, "1500.00",
        BudgetYear("2026", 0.2, totals(), totals(), yearly), averages, previousYear,
    )

    // --- months ---------------------------------------------------------------------------

    @Test
    fun `month validation is strict YYYY-MM`() {
        assertTrue(isBudgetMonth("2026-09"))
        assertFalse(isBudgetMonth("2026-13"))
        assertFalse(isBudgetMonth("2026-9"))
        assertFalse(isBudgetMonth(null))
    }

    @Test
    fun `months shift across year edges`() {
        assertEquals("2026-01", shiftBudgetMonth("2025-12", 1))
        assertEquals("2025-12", shiftBudgetMonth("2026-01", -1))
        assertEquals("2026-03", shiftBudgetMonth("2026-03", 0))
    }

    @Test
    fun `the current month comes from the given day`() {
        assertEquals("2026-10", currentBudgetMonth(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `months are written in English`() {
        assertEquals("September 2026", formatBudgetMonth("2026-09"))
        assertEquals("Sep 2026", formatBudgetMonth("2026-09", short = true))
    }

    // --- reading a month ------------------------------------------------------------------

    @Test
    fun `pace only shows while the period is running`() {
        assertNull(runningPace(0.0))
        assertNull(runningPace(1.0))
        assertEquals(0.4, runningPace(0.4)!!, 0.0)
    }

    @Test
    fun `rows are grouped expenses then income and a null list is empty`() {
        val groups = groupBudgetsByType(listOf(item(1, "income"), item(2, "expense"), item(3, "expense")))
        assertEquals(listOf("expense", "income"), groups.map { it.type })
        assertEquals(listOf(2, 3), groups[0].items.map { it.category_id })
        assertTrue(groupBudgetsByType(null).all { it.items.isEmpty() })
    }

    @Test
    fun `empty means no monthly and no yearly budgets - null lists included`() {
        assertTrue(hasNoBudgets(month(items = null, yearly = null)))
        assertFalse(hasNoBudgets(month(items = null, yearly = listOf(item()))))
        assertFalse(hasNoBudgets(month(items = listOf(item()))))
    }

    @Test
    fun `balance is actual income minus actual expenses`() {
        val m = month(expense = totals(actual = "400"), income = totals(actual = "1800.50"))
        assertEquals(1400.5, budgetBalance(m), 0.0)
        assertEquals(-50.0, budgetBalance(month(expense = totals(actual = "50"))), 0.0)
    }

    @Test
    fun `off budget is unbudgeted plus overspent, expenses only`() {
        val m = month(expense = totals(unbudgeted = "30", overspent = "20.5"), income = totals(unbudgeted = "99"))
        assertEquals(50.5, offBudget(m), 0.0)
    }

    // --- rows -----------------------------------------------------------------------------

    @Test
    fun `an expense speaks of what is left or over`() {
        assertEquals("50,00${nbsp}€ left", budgetNote(item(remaining = "50")))
        assertEquals("0,00${nbsp}€ left", budgetNote(item(remaining = "0")))
        assertEquals("74,60${nbsp}€ over", budgetNote(item(remaining = "-74.60")))
    }

    @Test
    fun `income speaks of what is left to earn or above`() {
        assertEquals("200,00${nbsp}€ to go", budgetNote(item(type = "income", remaining = "200")))
        assertEquals("30,00${nbsp}€ above", budgetNote(item(type = "income", remaining = "-30")))
        // Exactly on target is "above", as on the web (remaining > 0 is the only "to go").
        assertEquals("0,00${nbsp}€ above", budgetNote(item(type = "income", remaining = "0")))
    }

    @Test
    fun `an overspent bar is full, not overflowing`() {
        assertEquals(1f, budgetFill(item(progress = 1.213)), 0f)
        assertEquals(0.5f, budgetFill(item(progress = 0.5)), 0f)
        assertEquals(0f, budgetFill(item(progress = -1.0)), 0f)
    }

    @Test
    fun `percent is rounded and a met income budget is a tick`() {
        assertEquals("121%", budgetPercentLabel(item(progress = 1.213)))
        assertEquals("50%", budgetPercentLabel(item(progress = 0.5)))
        assertEquals("✓", budgetPercentLabel(item(type = "income", status = "met", progress = 1.0)))
    }

    @Test
    fun `since is a short month for monthly and a bare year for yearly`() {
        assertEquals("since Mar 2026", budgetSince(item(since = "2026-03")))
        assertEquals("since 2025", budgetSince(item(period = "yearly", since = "2025")))
    }

    // --- the form -------------------------------------------------------------------------

    @Test
    fun `transfers cannot be budgeted`() {
        fun cat(id: Int, type: String) = Category(id, "c$id", null, type, "")
        val ids = budgetableCategories(listOf(cat(1, "expense"), cat(2, "transfer"), cat(3, "income"))).map { it.id }
        assertEquals(listOf(1, 3), ids)
    }

    @Test
    fun `the suggestion is the average when monthly and last year when yearly`() {
        val m = month(
            averages = listOf(BudgetAmount(6, "739.12")),
            previousYear = listOf(BudgetAmount(6, "8000.00")),
        )
        assertEquals("739.12", budgetSuggestion(m, 6, "monthly"))
        assertEquals("8000.00", budgetSuggestion(m, 6, "yearly"))
        assertNull(budgetSuggestion(m, 7, "monthly"))
        assertNull(budgetSuggestion(m, null, "monthly"))
        assertNull(budgetSuggestion(month(), 6, "monthly"))
    }

    @Test
    fun `the form needs a category and a number that is not negative`() {
        assertEquals(BudgetFormCheck.Invalid(categoryError = true, amountError = false), checkBudgetForm(null, "10"))
        assertEquals(BudgetFormCheck.Invalid(categoryError = false, amountError = true), checkBudgetForm(1, ""))
        assertEquals(BudgetFormCheck.Invalid(categoryError = false, amountError = true), checkBudgetForm(1, "abc"))
        assertEquals(BudgetFormCheck.Invalid(categoryError = false, amountError = true), checkBudgetForm(1, "-1"))
        assertEquals(BudgetFormCheck.Invalid(categoryError = true, amountError = true), checkBudgetForm(null, ""))
    }

    @Test
    fun `zero is a real budget and a decimal comma is a point`() {
        assertEquals(BudgetFormCheck.Ok(1, "0.00"), checkBudgetForm(1, "0"))
        assertEquals(BudgetFormCheck.Ok(1, "12.50"), checkBudgetForm(1, "12,5"))
        assertEquals(BudgetFormCheck.Ok(1, "350.00"), checkBudgetForm(1, " 350 "))
    }

    @Test
    fun `an existing amount is shown without trailing zeros`() {
        assertEquals("350", budgetAmountText("350.00"))
        assertEquals("12.5", budgetAmountText("12.50"))
        assertEquals("739.12", budgetAmountText("739.12"))
    }
}
