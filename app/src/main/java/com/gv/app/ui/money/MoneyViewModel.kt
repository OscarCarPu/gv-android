package com.gv.app.ui.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.MoneyData
import com.gv.app.data.repository.MoneyRepository
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.OverviewTransaction
import com.gv.app.domain.model.Transaction
import com.gv.app.domain.model.UpdateTransactionRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class MoneyUiState {
    data object Loading : MoneyUiState()
    data class Loaded(val data: MoneyData) : MoneyUiState()
    data class Error(val message: String) : MoneyUiState()
}

/**
 * The transactions list on the Overview tab: the last 30 days, or — with an account picked —
 * that account's whole history, folded to [TX_FOLD_LIMIT] and unfolded [TX_EXPAND_STEP] at a time.
 */
data class TxListState(
    val visible: List<OverviewTransaction> = emptyList(),
    val total: Int = 0,
    val filteringAccountId: Int? = null,
    val loading: Boolean = false,
) {
    val filtering: Boolean get() = filteringAccountId != null
    val hasMore: Boolean get() = visible.size < total
    val remaining: Int get() = total - visible.size
}

/**
 * The Money screen. Online-first: reads are live (there is no cache — see [MoneyRepository]),
 * writes are refused offline, and after every write everything is re-read, because account
 * totals are maintained by a database trigger the client cannot second-guess.
 *
 * Deletes are immediate, as on the web: no confirmation dialog. An account with transactions and
 * a category still in use cannot be deleted at all — the API answers 409, and it is reported by
 * name rather than as a generic error.
 */
class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: MoneyRepository = app.container.moneyRepository

    private val _state = MutableStateFlow<MoneyUiState>(MoneyUiState.Loading)
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val selectedAccountId = MutableStateFlow<Int?>(null)
    private val history = MutableStateFlow<List<OverviewTransaction>>(emptyList())
    private val loadingHistory = MutableStateFlow(false)
    private val visibleCount = MutableStateFlow(TX_FOLD_LIMIT)

    val transactions: StateFlow<TxListState> =
        combine(_state, selectedAccountId, history, loadingHistory, visibleCount) { state, account, hist, loading, count ->
            val source = if (account != null) hist else (state as? MoneyUiState.Loaded)?.data?.overview?.recent_transactions.orEmpty()
            TxListState(
                visible = source.take(count),
                total = source.size,
                filteringAccountId = account,
                loading = loading,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxListState())

    init {
        refresh()
    }

    // ----- Reads -----

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    /** Re-read everything, and the picked account's history if one is picked. */
    private suspend fun reload() {
        val first = _state.value !is MoneyUiState.Loaded
        if (first) _state.value = MoneyUiState.Loading
        when (val r = repo.load()) {
            is ApiResult.Success -> _state.value = MoneyUiState.Loaded(r.data)
            is ApiResult.Failure ->
                if (first) _state.value = MoneyUiState.Error(r.message) else _toast.emit(r.message)
        }
        if (selectedAccountId.value != null) loadHistory()
    }

    /** Switch the list to an account's full history, or back to the recent list with null. */
    fun selectAccount(id: Int?) {
        selectedAccountId.value = id
        visibleCount.value = TX_FOLD_LIMIT
        viewModelScope.launch { loadHistory() }
    }

    fun showMore() = visibleCount.update { it + TX_EXPAND_STEP }

    private suspend fun loadHistory() {
        val id = selectedAccountId.value
        val data = (_state.value as? MoneyUiState.Loaded)?.data
        if (id == null || data == null) {
            history.value = emptyList()
            return
        }
        loadingHistory.value = true
        when (val r = repo.accountHistory(id)) {
            is ApiResult.Success ->
                history.value = r.data.map { toOverviewTransaction(it, data.accounts, data.categories) }
            is ApiResult.Failure -> {
                _toast.emit(r.message)
                history.value = emptyList()
            }
        }
        loadingHistory.value = false
    }

    // ----- Transactions -----

    /** The overview rows are name-based; editing needs the real ids, so the full row is fetched. */
    fun loadTransaction(id: Int, onLoaded: (Transaction?) -> Unit) {
        viewModelScope.launch {
            when (val r = repo.transaction(id)) {
                is ApiResult.Success -> onLoaded(r.data)
                is ApiResult.Failure -> {
                    _toast.emit(r.message)
                    onLoaded(null)
                }
            }
        }
    }

    fun saveTransaction(existingId: Int?, form: TransactionFormCheck.Ok, occurredAtIso: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = if (existingId != null) {
                repo.updateTransaction(
                    existingId,
                    UpdateTransactionRequest(
                        type = form.type,
                        amount = form.amount,
                        account_id = form.accountId,
                        to_account_id = form.toAccountId,
                        category_id = form.categoryId,
                        description = form.description,
                        occurred_at = occurredAtIso,
                    ),
                )
            } else {
                repo.createTransaction(
                    CreateTransactionRequest(
                        type = form.type,
                        amount = form.amount,
                        account_id = form.accountId,
                        to_account_id = form.toAccountId,
                        category_id = form.categoryId,
                        description = form.description,
                        occurred_at = occurredAtIso,
                    ),
                )
            }
            finishSave(result, onDone)
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch { finishDelete(Deletable.TRANSACTION, repo.deleteTransaction(id)) }
    }

    // ----- Accounts -----

    fun saveAccount(id: Int?, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            finishSave(if (id != null) repo.updateAccount(id, name) else repo.createAccount(name), onDone)
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            // Deleting the account being viewed would leave the list filtered on nothing.
            if (selectedAccountId.value == account.id) selectedAccountId.value = null
            finishDelete(Deletable.ACCOUNT, repo.deleteAccount(account.id))
        }
    }

    // ----- Categories -----

    fun saveCategory(id: Int?, name: String, type: String, parentId: Int?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            finishSave(
                if (id != null) repo.updateCategory(id, name, type, parentId) else repo.createCategory(name, type, parentId),
                onDone,
            )
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { finishDelete(Deletable.CATEGORY, repo.deleteCategory(category.id)) }
    }

    // ----- Shared -----

    /** A save's failure is spoken in the server's own words; success re-reads everything. */
    private suspend fun finishSave(result: ApiResult<Unit>, onDone: (Boolean) -> Unit) {
        when (result) {
            is ApiResult.Success -> {
                onDone(true)
                reload()
            }
            is ApiResult.Failure -> {
                _toast.emit(result.message)
                onDone(false)
            }
        }
    }

    private suspend fun finishDelete(kind: Deletable, result: ApiResult<Unit>) {
        when (result) {
            is ApiResult.Success -> reload()
            is ApiResult.Failure -> _toast.emit(deleteFailureMessage(kind, result))
        }
    }
}
