package com.gv.app.ui.domotics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/**
 * Domotics → Lights.
 *
 * Unlike every other tab this one has no cached fallback: a bulb's state is only meaningful
 * live, so with no connection the screen says so rather than showing a stale picture of the
 * room. See [com.gv.app.data.repository.LightsRepository].
 */
@Composable
fun LightsScreen(vm: LightsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.initialLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = GvColors.Primary)
            }

            state.lights.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error ?: "No bulbs configured.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            summaryOf(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = GvColors.TextMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            OutlinedButton(onClick = { vm.setAll(false) }) { Text("All off") }
                            OutlinedButton(onClick = { vm.setAll(true) }) { Text("All on") }
                        }
                    }
                }

                items(state.lights, key = { it.id }) { light ->
                    LightCard(
                        light = light,
                        onTogglePower = { vm.togglePower(light) },
                        onBrightness = { vm.setBrightness(light, it) },
                        onColorTemp = { vm.setColorTemp(light, it) },
                    )
                }

                // A failed refresh with bulbs already on screen: keep them, say why they're stale.
                state.error?.let { error ->
                    item {
                        Text(
                            error,
                            style = MaterialTheme.typography.labelSmall,
                            color = GvColors.Warning,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun summaryOf(state: LightsUiState): String {
    val total = state.lights.size
    val on = state.lights.count { it.power && it.online }
    val offline = state.lights.count { !it.online }
    val base = "$on of $total on"
    return if (offline > 0) "$base · $offline unreachable" else base
}
