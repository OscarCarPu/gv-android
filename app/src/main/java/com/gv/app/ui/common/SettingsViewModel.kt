package com.gv.app.ui.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.local.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = app.container.themeStore

    val theme = store.theme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThemePreference.DARK,
    )

    /** Toggle dark ↔ light. */
    fun cycleTheme() {
        viewModelScope.launch {
            store.set(if (theme.value == ThemePreference.DARK) ThemePreference.LIGHT else ThemePreference.DARK)
        }
    }
}
