package com.gv.app.ui.money

import com.gv.app.data.repository.ApiResult
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.OverviewTransaction
import com.gv.app.domain.model.Transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Money rules that are easy to get subtly wrong, ported from gv-web (`utils/money.ts`,
 * `overview.svelte.ts`, `transactionForm.svelte.ts`, `deleteConflict.ts`) so both apps agree.
 *
 * Every monetary field is a `NUMERIC(15,2)` serialized as a *string*; it is parsed only here,
 * at the point of display or arithmetic, and sent back as a two-decimal string.
 */

// ---------- Summary tiles ----------

data class OverviewKpis(
    val balance: Double,
    /** `+`, `−` or nothing, so a zero balance is not signed. */
    val balancePrefix: String,
    /** Savings as a percentage of income; 0 when there was no income to save from. */
    val savingsRate: Double,
    /** Change in balance against last month, relative to the size of last month's balance. */
    val balanceChangePct: Double,
    /** False when last month's balance was zero, and the change is undefined ("—"). */
    val hasPrevBalance: Boolean,
)

fun deriveOverviewKpis(overview: Overview): OverviewKpis {
    val balance = overview.month.balance.toDoubleOrNull() ?: 0.0
    val income = overview.month.income.toDoubleOrNull() ?: 0.0
    val prev = overview.previous_month.balance.toDoubleOrNull() ?: 0.0
    val hasPrev = prev != 0.0
    return OverviewKpis(
        balance = balance,
        balancePrefix = if (balance > 0) "+" else if (balance < 0) "−" else "",
        savingsRate = if (income > 0) balance / income * 100 else 0.0,
        // Divided by |prev|, not prev: going from −100 to −50 is an improvement, and dividing by
        // a negative would report it as a fall.
        balanceChangePct = if (hasPrev) (balance - prev) / abs(prev) * 100 else 0.0,
        hasPrevBalance = hasPrev,
    )
}

/** `12.3%` — one decimal, with a dot, whatever the phone's locale. */
fun formatPercent(value: Double): String = String.format(Locale.ROOT, "%.1f%%", value)

// ---------- Recent transactions and account history ----------

/** The list folds at 15 and each "N more" reveals 10, the web's numbers for a plain list. */
const val TX_FOLD_LIMIT = 15
const val TX_EXPAND_STEP = 10

/**
 * An account's history comes back id-based ([Transaction]) while the recent list is name-based
 * ([OverviewTransaction]); this puts one in the shape of the other so one row renders both.
 */
fun toOverviewTransaction(tx: Transaction, accounts: List<Account>, categories: List<Category>): OverviewTransaction {
    fun accountName(id: Int?) = id?.let { i -> accounts.firstOrNull { it.id == i }?.name }
    return OverviewTransaction(
        id = tx.id,
        type = tx.type,
        amount = tx.amount,
        account_name = accountName(tx.account_id) ?: "—",
        to_account_name = accountName(tx.to_account_id),
        category_name = tx.category_id?.let { i -> categories.firstOrNull { it.id == i }?.name },
        description = tx.description,
        occurred_at = tx.occurred_at,
    )
}

// ---------- The transaction form ----------

sealed interface TransactionFormCheck {
    /** Ready to send: [amount] is a two-decimal string and the transfer target is null unless a transfer. */
    data class Ok(
        val type: String,
        val amount: String,
        val accountId: Int,
        val toAccountId: Int?,
        val categoryId: Int,
        val description: String?,
    ) : TransactionFormCheck

    data class Invalid(
        val amountError: Boolean,
        val accountError: Boolean,
        val categoryError: Boolean,
        val toAccountError: Boolean,
    ) : TransactionFormCheck
}

/**
 * The web's `TransactionForm.validate`: a positive amount, a source account, a category (for
 * every type — the category's own type must match the transaction's, which the form's option
 * list enforces), and for a transfer a destination that differs from the source.
 *
 * A comma is read as a decimal point: the decimal keypad on a phone set to a Spanish locale
 * types one, and `"12,5".toDouble()` is not a number.
 */
fun checkTransactionForm(
    type: String,
    amountText: String,
    accountId: Int?,
    toAccountId: Int?,
    categoryId: Int?,
    description: String,
): TransactionFormCheck {
    val amount = amountText.trim().replace(',', '.').toDoubleOrNull()
    val amountError = amount == null || amount.isNaN() || amount.isInfinite() || amount <= 0.0
    val accountError = accountId == null
    val categoryError = categoryId == null
    val toAccountError = type == "transfer" && (toAccountId == null || toAccountId == accountId)
    if (amountError || accountError || categoryError || toAccountError) {
        return TransactionFormCheck.Invalid(amountError, accountError, categoryError, toAccountError)
    }
    return TransactionFormCheck.Ok(
        type = type,
        amount = String.format(Locale.ROOT, "%.2f", amount),
        accountId = accountId!!,
        toAccountId = if (type == "transfer") toAccountId else null,
        categoryId = categoryId!!,
        description = description.trim().ifEmpty { null },
    )
}

// ---------- When it happened ----------

private val WallClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

/**
 * A transaction's `occurred_at` is the wall-clock time it was entered, stamped as if it were
 * UTC — gv-web's `toISOString` / `toLocalDatetime`, and the same idea as a task's `due_at`. It is
 * *not* a real instant, and converting it to or from the phone's zone would move a purchase made
 * at 00:30 onto the previous day (the list groups by the first ten characters), and would show a
 * row created on the web two hours out.
 */
fun wallClockToIso(local: LocalDateTime): String = local.withNano(0).format(WallClockFormat)

/** The digits of an API timestamp read as a wall clock; null when they are not one. */
fun isoToWallClock(iso: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(iso.take(19)) }.getOrNull()

// ---------- Deleting what other things depend on ----------

/**
 * What a delete of each kind says when it fails. An account with transactions and a category
 * that is still referenced answer `409`; that is an ordinary "no, it is in use", so it gets its
 * own message instead of the generic error.
 */
enum class Deletable(val conflictNeedle: String?, val conflictMessage: String, val errorMessage: String) {
    TRANSACTION(null, "", "Error deleting transaction"),
    ACCOUNT("transactions", "Account has associated transactions", "Error deleting account"),
    CATEGORY("referenced", "Category is in use", "Error deleting category"),
}

fun deleteFailureMessage(kind: Deletable, failure: ApiResult.Failure): String = when {
    // Offline is a state, not a fault, and already says exactly what to do.
    failure.offline -> failure.message
    kind.conflictNeedle != null && failure.code == 409 && failure.message.contains(kind.conflictNeedle) -> kind.conflictMessage
    else -> kind.errorMessage
}
