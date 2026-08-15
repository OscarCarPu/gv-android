package com.gv.app.ui.common

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.container
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SyncStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container

    val isOnline = container.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), container.connectivityObserver.isOnline())
}

/**
 * Self-hiding strip that says when the app is read-only. Offline is the only state worth a
 * banner now: there is no write queue to report on, because writes are refused while offline
 * rather than deferred. Absent whenever there is a connection.
 */
@Composable
fun SyncStatusBanner(
    modifier: Modifier = Modifier,
    vm: SyncStatusViewModel = viewModel(),
) {
    val online by vm.isOnline.collectAsStateWithLifecycle()

    val state: BannerState? = if (online) {
        null
    } else {
        BannerState(
            Icons.Outlined.CloudOff,
            "Offline — showing saved data, editing is off",
            GvColors.Warning,
        )
    }

    // Hold the last non-null state so the exit animation still has content to draw.
    var lastShown by remember { mutableStateOf<BannerState?>(null) }
    if (state != null) lastShown = state

    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val shown = state ?: lastShown ?: return@AnimatedVisibility
        val spacing = LocalSpacing.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(shown.accent.copy(alpha = 0.14f))
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(shown.icon, contentDescription = null, tint = shown.accent, modifier = Modifier.size(16.dp))
            Text(shown.message, style = MaterialTheme.typography.labelMedium, color = GvColors.Text)
        }
    }
}

private data class BannerState(val icon: ImageVector, val message: String, val accent: Color)
