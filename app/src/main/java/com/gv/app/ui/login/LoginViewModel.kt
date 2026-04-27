package com.gv.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.BuildConfig
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.data.local.TokenManager
import com.gv.app.domain.model.ErrorResponse
import com.gv.app.domain.model.LoginRequest
import com.gv.app.domain.model.TwoFactorRequest
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class AwaitingTwoFactor(
        val tempToken: String,
        val errorMessage: String? = null
    ) : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val api: ApiService = RetrofitClient.apiService,
    private val tokenManager: TokenManager = RetrofitClient.tokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val gson = Gson()

    init {
        // Debug-only bypass: skips the real login + 2FA round-trip so the post-login UI is
        // reachable without a backend. Stripped from release builds via BuildConfig.DEBUG.
        if (BuildConfig.DEBUG && tokenManager.tokenFlow.value == null) {
            tokenManager.saveToken("debug-bypass-token")
            _uiState.value = LoginUiState.Success
        }
    }

    fun submitPassword(password: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(password))
                if (response.isSuccessful) {
                    val token = response.body()!!.token
                    _uiState.value = LoginUiState.AwaitingTwoFactor(token)
                } else {
                    val message = parseError(response.errorBody()?.string())
                    _uiState.value = LoginUiState.Error(message)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun submitTwoFactorCode(code: String) {
        val current = _uiState.value as? LoginUiState.AwaitingTwoFactor ?: return
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val response = api.login2fa(TwoFactorRequest(current.tempToken, code))
                if (response.isSuccessful) {
                    val token = response.body()!!.token
                    tokenManager.saveToken(token)
                    _uiState.value = LoginUiState.Success
                } else {
                    val message = parseError(response.errorBody()?.string())
                    _uiState.value = LoginUiState.AwaitingTwoFactor(
                        tempToken = current.tempToken,
                        errorMessage = message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.AwaitingTwoFactor(
                    tempToken = current.tempToken,
                    errorMessage = e.message ?: "Network error"
                )
            }
        }
    }

    fun clearError() {
        when (val current = _uiState.value) {
            is LoginUiState.Error -> _uiState.value = LoginUiState.Idle
            is LoginUiState.AwaitingTwoFactor -> {
                if (current.errorMessage != null) {
                    _uiState.value = current.copy(errorMessage = null)
                }
            }
            else -> {}
        }
    }

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            gson.fromJson(body, ErrorResponse::class.java).error
        } catch (_: Exception) {
            "Unknown error"
        }
    }
}
