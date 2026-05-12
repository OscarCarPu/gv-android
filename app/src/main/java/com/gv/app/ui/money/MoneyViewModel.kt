package com.gv.app.ui.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateAccountRequest
import com.gv.app.domain.model.CreateCategoryRequest
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.Overview
import com.gv.app.domain.model.UpdateAccountRequest
import com.gv.app.domain.model.UpdateCategoryRequest
import com.gv.app.domain.model.UpdateTransactionRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoneyData(
    val overview: Overview,
    val accounts: List<Account>,
    val categories: List<Category>,
)

sealed class MoneyUiState {
    data object Loading : MoneyUiState()
    data class Loaded(val data: MoneyData) : MoneyUiState()
    data class Error(val message: String) : MoneyUiState()
}

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val api: ApiService = RetrofitClient.apiService

    private val _state = MutableStateFlow<MoneyUiState>(MoneyUiState.Loading)
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is MoneyUiState.Loaded) {
                _state.value = MoneyUiState.Loading
            }
            try {
                val (overview, accounts, categories) = coroutineLoad()
                _state.value = MoneyUiState.Loaded(MoneyData(overview, accounts, categories))
            } catch (e: Exception) {
                if (_state.value !is MoneyUiState.Loaded) {
                    _state.value = MoneyUiState.Error(e.message ?: "Network error")
                } else {
                    _toast.emit(e.message ?: "Network error")
                }
            }
        }
    }

    private suspend fun coroutineLoad(): Triple<Overview, List<Account>, List<Category>> {
        return kotlinx.coroutines.coroutineScope {
            val overviewDef = async {
                val r = api.getFinanceOverview()
                if (!r.isSuccessful) error("Failed to load overview")
                r.body()!!
            }
            val accountsDef = async {
                val r = api.listAccounts()
                if (!r.isSuccessful) error("Failed to load accounts")
                r.body() ?: emptyList()
            }
            val categoriesDef = async {
                val r = api.listCategories()
                if (!r.isSuccessful) error("Failed to load categories")
                r.body() ?: emptyList()
            }
            Triple(overviewDef.await(), accountsDef.await(), categoriesDef.await())
        }
    }

    // --- Transactions ---

    fun loadTransaction(id: Int, onLoaded: (com.gv.app.domain.model.Transaction?) -> Unit) {
        viewModelScope.launch {
            try {
                val r = api.getTransaction(id)
                if (r.isSuccessful) {
                    onLoaded(r.body())
                } else {
                    _toast.emit("Failed to load transaction")
                    onLoaded(null)
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
                onLoaded(null)
            }
        }
    }

    fun saveTransaction(id: Int?, req: CreateTransactionRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = if (id != null) {
                    api.updateTransaction(
                        id,
                        UpdateTransactionRequest(
                            type = req.type,
                            amount = req.amount,
                            account_id = req.account_id,
                            to_account_id = req.to_account_id,
                            category_id = req.category_id,
                            description = req.description,
                            occurred_at = req.occurred_at ?: "",
                        ),
                    )
                } else {
                    api.createTransaction(req)
                }
                if (r.isSuccessful) {
                    onDone(true)
                    refresh()
                } else {
                    _toast.emit("Failed to save transaction")
                    onDone(false)
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
                onDone(false)
            }
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            try {
                val r = api.deleteTransaction(id)
                if (r.isSuccessful) {
                    refresh()
                } else {
                    _toast.emit("Failed to delete transaction")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    // --- Accounts ---

    fun saveAccount(id: Int?, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = if (id != null) {
                    api.updateAccount(id, UpdateAccountRequest(name))
                } else {
                    api.createAccount(CreateAccountRequest(name))
                }
                if (r.isSuccessful) {
                    onDone(true)
                    refresh()
                } else {
                    _toast.emit("Failed to save account")
                    onDone(false)
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
                onDone(false)
            }
        }
    }

    fun deleteAccount(id: Int) {
        viewModelScope.launch {
            try {
                val r = api.deleteAccount(id)
                if (r.isSuccessful) {
                    refresh()
                } else {
                    _toast.emit("Failed to delete account (may have transactions)")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }

    // --- Categories ---

    fun saveCategory(id: Int?, name: String, type: String, parentId: Int?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = if (id != null) {
                    api.updateCategory(id, UpdateCategoryRequest(name, parentId, type))
                } else {
                    api.createCategory(CreateCategoryRequest(name, parentId, type))
                }
                if (r.isSuccessful) {
                    onDone(true)
                    refresh()
                } else {
                    _toast.emit("Failed to save category")
                    onDone(false)
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
                onDone(false)
            }
        }
    }

    fun deleteCategory(id: Int) {
        viewModelScope.launch {
            try {
                val r = api.deleteCategory(id)
                if (r.isSuccessful) {
                    refresh()
                } else {
                    _toast.emit("Failed to delete category (may be in use)")
                }
            } catch (e: Exception) {
                _toast.emit(e.message ?: "Network error")
            }
        }
    }
}
