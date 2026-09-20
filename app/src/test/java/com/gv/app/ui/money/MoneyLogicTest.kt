package com.gv.app.ui.money

import com.gv.app.data.repository.ApiResult
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.OverviewMonth
import com.gv.app.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Money that renders plausibly while being wrong: a sign, a percentage base, a decimal comma. */
class MoneyLogicTest {

    private val nbsp = " "

    // --- formatting -----------------------------------------------------------------------

    @Test
    fun `money is es-ES with grouping even on four digits`() {
        assertEquals("1.234,56${nbsp}€", formatMoney("1234.56"))
        assertEquals("1.234.567,89${nbsp}€", formatMoney(1234567.891))
        assertEquals("0,50${nbsp}€", formatMoney("0.5"))
        assertEquals("999,00${nbsp}€", formatMoney("999"))
    }

    @Test
    fun `negative money keeps its sign and unreadable money is shown as it came`() {
        assertEquals("-1.000,00${nbsp}€", formatMoney("-1000"))
        assertEquals("n/a", formatMoney("n/a"))
    }

    // --- KPIs -----------------------------------------------------------------------------

    private fun overview(income: String = "100", balance: String = "40", prevBalance: String = "20") = Overview(
        accounts_total = "0",
        month = OverviewMonth(income, "0", balance),
        previous_month = OverviewMonth("0", "0", prevBalance),
        recent_transactions = emptyList(),
    )

    @Test
    fun `savings is balance over income, and zero without income`() {
        assertEquals(40.0, deriveOverviewKpis(overview(income = "100", balance = "40")).savingsRate, 0.0001)
        assertEquals(0.0, deriveOverviewKpis(overview(income = "0", balance = "-30")).savingsRate, 0.0)
    }

    @Test
    fun `the balance prefix is signed only when there is a sign to show`() {
        assertEquals("+", deriveOverviewKpis(overview(balance = "1")).balancePrefix)
        assertEquals("−", deriveOverviewKpis(overview(balance = "-1")).balancePrefix)
        assertEquals("", deriveOverviewKpis(overview(balance = "0")).balancePrefix)
    }

    @Test
    fun `the change against last month is relative to its size, not its sign`() {
        assertEquals(100.0, deriveOverviewKpis(overview(balance = "40", prevBalance = "20")).balanceChangePct, 0.0001)
        // -100 -> -50 is an improvement. Dividing by the raw -100 would report a fall of 50%.
        assertEquals(50.0, deriveOverviewKpis(overview(balance = "-50", prevBalance = "-100")).balanceChangePct, 0.0001)
    }

    @Test
    fun `no change is reported when last month's balance was zero`() {
        val k = deriveOverviewKpis(overview(prevBalance = "0"))
        assertFalse(k.hasPrevBalance)
        assertEquals(0.0, k.balanceChangePct, 0.0)
    }

    @Test
    fun `percentages use a dot whatever the locale`() {
        assertEquals("12.5%", formatPercent(12.5))
        assertEquals("-3.0%", formatPercent(-3.0))
    }

    // --- transaction form -----------------------------------------------------------------

    private fun check(
        type: String = "expense",
        amount: String = "12.50",
        account: Int? = 1,
        to: Int? = null,
        category: Int? = 5,
        description: String = "",
    ) = checkTransactionForm(type, amount, account, to, category, description)

    @Test
    fun `a valid expense is normalised to two decimals`() {
        val ok = check(amount = "12.5", description = "  lunch ") as TransactionFormCheck.Ok
        assertEquals("12.50", ok.amount)
        assertEquals("lunch", ok.description)
        assertNull(ok.toAccountId)
    }

    @Test
    fun `a decimal comma is a decimal point`() {
        assertEquals("12.50", (check(amount = "12,5") as TransactionFormCheck.Ok).amount)
    }

    @Test
    fun `an amount must be a positive number`() {
        val bad = TransactionFormCheck.Invalid(amountError = true, accountError = false, categoryError = false, toAccountError = false)
        assertEquals(bad, check(amount = ""))
        assertEquals(bad, check(amount = "0"))
        assertEquals(bad, check(amount = "-5"))
        assertEquals(bad, check(amount = "abc"))
    }

    @Test
    fun `an account and a category are required`() {
        val r = check(account = null, category = null) as TransactionFormCheck.Invalid
        assertTrue(r.accountError)
        assertTrue(r.categoryError)
        assertFalse(r.amountError)
    }

    @Test
    fun `a transfer needs a different destination`() {
        assertTrue((check(type = "transfer", to = null) as TransactionFormCheck.Invalid).toAccountError)
        assertTrue((check(type = "transfer", account = 1, to = 1) as TransactionFormCheck.Invalid).toAccountError)
        assertEquals(2, (check(type = "transfer", account = 1, to = 2) as TransactionFormCheck.Ok).toAccountId)
    }

    @Test
    fun `a destination is dropped unless it is a transfer`() {
        assertNull((check(type = "expense", to = 2) as TransactionFormCheck.Ok).toAccountId)
    }

    // --- account history ------------------------------------------------------------------

    @Test
    fun `an id-based transaction gets the names of what it points at`() {
        val accounts = listOf(Account(1, "Checking", "0", ""), Account(2, "Savings", "0", ""))
        val categories = listOf(Category(9, "Food", null, "expense", ""))
        val tx = Transaction(7, "transfer", "5.00", 1, 2, 9, "top up", "2026-09-20T10:00:00Z", "")
        val row = toOverviewTransaction(tx, accounts, categories)
        assertEquals("Checking", row.account_name)
        assertEquals("Savings", row.to_account_name)
        assertEquals("Food", row.category_name)
    }

    @Test
    fun `a missing account or category degrades instead of failing`() {
        val tx = Transaction(7, "expense", "5.00", 99, null, null, null, "2026-09-20T10:00:00Z", "")
        val row = toOverviewTransaction(tx, emptyList(), emptyList())
        assertEquals("—", row.account_name)
        assertNull(row.to_account_name)
        assertNull(row.category_name)
    }

    // --- delete conflicts -----------------------------------------------------------------

    @Test
    fun `an account with transactions and a category in use get their own message`() {
        val acc = ApiResult.Failure("account has transactions; delete them first", code = 409)
        val cat = ApiResult.Failure("category is referenced by transactions or other categories", code = 409)
        assertEquals("Account has associated transactions", deleteFailureMessage(Deletable.ACCOUNT, acc))
        assertEquals("Category is in use", deleteFailureMessage(Deletable.CATEGORY, cat))
    }

    @Test
    fun `any other failure is the generic error, and offline says so`() {
        assertEquals("Error deleting account", deleteFailureMessage(Deletable.ACCOUNT, ApiResult.Failure("boom", code = 500)))
        // A 409 whose message is not the known one is not "in use".
        assertEquals("Error deleting category", deleteFailureMessage(Deletable.CATEGORY, ApiResult.Failure("something else", code = 409)))
        assertEquals("Error deleting transaction", deleteFailureMessage(Deletable.TRANSACTION, ApiResult.Failure("x", code = 409)))
        val offline = ApiResult.offline()
        assertEquals(offline.message, deleteFailureMessage(Deletable.ACCOUNT, offline))
    }

    // --- category picker ------------------------------------------------------------------

    @Test
    fun `a category and everything under it is unavailable as its own parent`() {
        val cats = listOf(
            Category(1, "Food", null, "expense", ""),
            Category(2, "Groceries", 1, "expense", ""),
            Category(3, "Organic", 2, "expense", ""),
            Category(4, "Travel", null, "expense", ""),
        )
        assertEquals(setOf(2, 3), collectDescendantIds(cats, 2))
        assertEquals(setOf(1, 2, 3), collectDescendantIds(cats, 1))
        assertEquals(setOf(4), collectDescendantIds(cats, 4))
    }

    // --- when it happened -----------------------------------------------------------------

    @Test
    fun `a wall clock is stamped as-is, not converted to a zone`() {
        assertEquals("2026-09-20T00:30:00Z", wallClockToIso(java.time.LocalDateTime.of(2026, 9, 20, 0, 30, 0)))
    }

    @Test
    fun `the digits of a timestamp come back as the wall clock they were`() {
        assertEquals(java.time.LocalDateTime.of(2026, 9, 20, 10, 0, 0), isoToWallClock("2026-09-20T10:00:00Z"))
        assertEquals(java.time.LocalDateTime.of(2026, 9, 20, 10, 0, 5), isoToWallClock("2026-09-20T10:00:05.123456Z"))
        assertNull(isoToWallClock("not a date"))
    }

    @Test
    fun `stamping and reading are inverses in any zone`() {
        val original = java.time.LocalDateTime.of(2026, 3, 29, 2, 30, 0) // inside a spring-forward gap in Madrid
        assertEquals(original, isoToWallClock(wallClockToIso(original)))
    }

    // --- category tree --------------------------------------------------------------------

    private val tree = listOf(
        Category(1, "Food", null, "expense", ""),
        Category(2, "Groceries", 1, "expense", ""),
        Category(3, "Organic", 2, "expense", ""),
        Category(4, "Travel", null, "expense", ""),
    )

    private fun visibleIds(expanded: Set<Int>) = visibleCategoryRows(buildCategoryTreeRows(tree), expanded).map { it.category.id }

    @Test
    fun `the tree starts with every branch closed`() {
        assertEquals(listOf(1, 4), visibleIds(emptySet()))
    }

    @Test
    fun `opening a branch shows its children, one level at a time`() {
        assertEquals(listOf(1, 2, 4), visibleIds(setOf(1)))
        assertEquals(listOf(1, 2, 3, 4), visibleIds(setOf(1, 2)))
    }

    @Test
    fun `a grandchild stays hidden when only its grandparent is open`() {
        // 2 is open but 1 (its parent) is not, so neither 2 nor 3 can be seen.
        assertEquals(listOf(1, 4), visibleIds(setOf(2)))
    }
}
