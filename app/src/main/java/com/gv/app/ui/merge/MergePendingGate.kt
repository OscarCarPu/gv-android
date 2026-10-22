package com.gv.app.ui.merge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gv.app.BuildConfig
import com.gv.app.data.api.RetrofitClient
import com.gv.app.data.repository.MergeMonitor
import com.gv.app.data.repository.MergePending
import com.gv.app.data.repository.safeApiCall
import com.gv.app.ui.theme.GvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MS = 5_000L

/**
 * Covers [content] with a blocking screen while gv-api is restoring data after a failover; it
 * only waits for the hold to end.
 */
@Composable
fun MergePendingGate(content: @Composable () -> Unit) {
    val pending by MergePending.active.collectAsStateWithLifecycle()
    val monitor = remember {
        MergeMonitor(
            fetch = { safeApiCall { RetrofitClient.apiService.mergeStatus() } },
            semiprivate = BuildConfig.SEMIPRIVATE,
        )
    }
    val scope = rememberCoroutineScope()

    suspend fun check() {
        if (!monitor.stillPending()) MergePending.clear()
    }

    LaunchedEffect(pending) {
        while (pending) {
            delay(POLL_MS)
            check()
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (pending) {
            MergePendingScreen(onRetry = { scope.launch { check() } })
        }
    }
}

@Composable
private fun MergePendingScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = GvColors.Primary, modifier = Modifier.size(32.dp))
        Text(
            "Restoring your data",
            style = MaterialTheme.typography.titleMedium,
            color = GvColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "The backup server held your data while home was down and it is being brought back. " +
                "If it needs decisions, answer them in the web app.",
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}
