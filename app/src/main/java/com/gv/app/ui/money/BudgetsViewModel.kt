package com.gv.app.ui.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.MoneyRepository
import com.gv.app.domain.model.BudgetItem
import com.gv.app.domain.model.BudgetMonth
import com.gv.app.domain.model.OverviewTransaction
import com.gv.app.domain.model.SetBudgetRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class BudgetsUiState {
    data object Loading : BudgetsUiState()
    data class Loaded(val data: BudgetMonth) : BudgetsUiState()
    data class Error(val message: String) : BudgetsUiState()
}

/** The row whose counted transactions are open, with what has been loaded for it. */
data class ExpandedBudget(
    val key: String,
    val transactions: List<OverviewTransaction> = emptyList(),
    val loading: Boolean = true,
    val visibleCount: Int = BUDGET_TX_FOLD_LIMIT,
) {
    val visible: List<OverviewTransaction> get() = transactions.take(visibleCount)
    val remaining: Int get() = (transactions.size - visibleCount).coerceAtLeast(0)
}

internal const val BUDGET_TX_FOLD_LIMIT = 15
internal const val BUDGET_TX_EXPAND_STEP = 10

/** One row's identity within a month; navigating to another month collapses it. */
fun budgetKey(month: String, item: BudgetItem): String = "$month:${item.period}:${item.category_id}"

/**
 * The Budgets tab. Like the rest of money it is live (no cache) and writes are refused offline;
 * after a save or a removal the month is re-read rather than patched, since the server rolls
 * nested budgets up and decides each row's status.
 */
class BudgetsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: MoneyRepository = app.container.moneyRepository

    private val _month = MutableStateFlow(currentBudgetMonth())
    val month: StateFlow<String> = _month.asStateFlow()

    private val _state = MutableStateFlow<BudgetsUiState>(BudgetsUiState.Loading)
    val state: StateFlow<BudgetsUiState> = _state.asStateFlow()

    private val _expanded = MutableStateFlow<ExpandedBudget?>(null)
    val expanded: StateFlow<ExpandedBudget?> = _expanded.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun goMonth(ym: String) {
        if (ym == _month.value) return
        _month.value = ym
        _expanded.value = null
        _state.value = BudgetsUiState.Loading
        refresh()
    }

    private suspend fun load() {
        val requested = _month.value
        val first = _state.value !is BudgetsUiState.Loaded
        when (val r = repo.budgets(requested)) {
            is ApiResult.Success -> if (requested == _month.value) _state.value = BudgetsUiState.Loaded(r.data)
            is ApiResult.Failure ->
                if (requested == _month.value) {
                    if (first) _state.value = BudgetsUiState.Error(r.message) else _toast.emit(r.message)
                }
        }
    }

    // ----- Counted transactions -----

    /** Expand a row and load what it counted, or collapse it if it was open. */
    fun toggle(item: BudgetItem) {
        val key = budgetKey(_month.value, item)
        if (_expanded.value?.key == key) {
            _expanded.value = null
            return
        }
        _expanded.value = ExpandedBudget(key)
        viewModelScope.launch {
            when (val r = repo.budgetTransactions(item.category_id, _month.value, item.period)) {
                is ApiResult.Success -> _expanded.update { if (it?.key == key) it.copy(transactions = r.data, loading = false) else it }
                is ApiResult.Failure -> {
                    _expanded.update { if (it?.key == key) it.copy(loading = false) else it }
                    _toast.emit("Error loading transactions")
                }
            }
        }
    }

    fun showMore() = _expanded.update {
        it?.copy(visibleCount = minOf(it.visibleCount + BUDGET_TX_EXPAND_STEP, it.transactions.size))
    }

    // ----- Writes -----

    /** [item] is null when creating; the period is the form's, the month is the one on screen. */
    fun save(item: BudgetItem?, categoryId: Int, amount: String, scope: String, period: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = repo.setBudget(categoryId, SetBudgetRequest(_month.value, amount, scope, period))
            finish(r, if (item != null) "Budget updated" else "Budget created", "Error saving budget", onDone)
        }
    }

    fun remove(item: BudgetItem, scope: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = repo.deleteBudget(item.category_id, _month.value, scope, item.period)
            finish(r, "Budget removed", "Error removing budget", onDone)
        }
    }

    private suspend fun finish(r: ApiResult<Unit>, ok: String, error: String, onDone: (Boolean) -> Unit) {
        when (r) {
            is ApiResult.Success -> {
                _expanded.value = null
                onDone(true)
                _toast.emit(ok)
                load()
            }
            is ApiResult.Failure -> {
                // Offline already says what to do; a server fault gets the web's words.
                _toast.emit(if (r.offline) r.message else error)
                onDone(false)
            }
        }
    }
}
