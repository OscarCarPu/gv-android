package com.gv.app.data.repository

import com.gv.app.data.api.ApiService
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateAccountRequest
import com.gv.app.domain.model.CreateCategoryRequest
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.Transaction
import com.gv.app.domain.model.UpdateAccountRequest
import com.gv.app.domain.model.UpdateCategoryRequest
import com.gv.app.domain.model.UpdateTransactionRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Everything the Money screen shows, read together so the three never disagree. */
data class MoneyData(
    val overview: Overview,
    val accounts: List<Account>,
    val categories: List<Category>,
)

/**
 * The money domain. Online-first, offline read-only, like everything else — with one difference:
 * **it keeps no cache.** Balances are what you decide things by, and a stale one is worse than
 * none; the screen says it cannot load rather than show last week's total. (Lights are the same;
 * the calendar caches because a stale calendar is still an answer.)
 *
 * Writes are refused offline by [OnlineGate]. After one the caller re-reads with [load]:
 * account totals are maintained by a database trigger, so the client cannot compute what they
 * became and never tries.
 */
class MoneyRepository(
    private val api: ApiService,
    private val gate: OnlineGate,
) {

    /** Overview, accounts and categories, fetched in parallel. All three or an error. */
    suspend fun load(): ApiResult<MoneyData> = coroutineScope {
        val overview = async { safeApiCall { api.getFinanceOverview() } }
        val accounts = async { safeApiCall { api.listAccounts() } }
        val categories = async { safeApiCall { api.listCategories() } }
        val o = overview.await()
        val a = accounts.await()
        val c = categories.await()
        when {
            o is ApiResult.Failure -> o
            a is ApiResult.Failure -> a
            c is ApiResult.Failure -> c
            else -> ApiResult.Success(
                MoneyData(
                    overview = (o as ApiResult.Success).data,
                    accounts = (a as ApiResult.Success).data,
                    categories = (c as ApiResult.Success).data,
                ),
            )
        }
    }

    // ----- Transactions -----

    /** One account's whole history, newest first as the API returns it. */
    suspend fun accountHistory(accountId: Int): ApiResult<List<Transaction>> =
        safeApiCall { api.listTransactions(accountId) }

    suspend fun transaction(id: Int): ApiResult<Transaction> = safeApiCall { api.getTransaction(id) }

    suspend fun createTransaction(request: CreateTransactionRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createTransaction(request) }.map { }
    }

    suspend fun updateTransaction(id: Int, request: UpdateTransactionRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateTransaction(id, request) }.map { }
    }

    suspend fun deleteTransaction(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteTransaction(id) }
    }

    // ----- Accounts -----

    suspend fun createAccount(name: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createAccount(CreateAccountRequest(name)) }.map { }
    }

    suspend fun updateAccount(id: Int, name: String): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateAccount(id, UpdateAccountRequest(name)) }.map { }
    }

    suspend fun deleteAccount(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteAccount(id) }
    }

    // ----- Categories -----

    suspend fun createCategory(name: String, type: String, parentId: Int?): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createCategory(CreateCategoryRequest(name, parentId, type)) }.map { }
    }

    suspend fun updateCategory(id: Int, name: String, type: String, parentId: Int?): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateCategory(id, UpdateCategoryRequest(name, parentId, type)) }.map { }
    }

    suspend fun deleteCategory(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteCategory(id) }
    }
}
