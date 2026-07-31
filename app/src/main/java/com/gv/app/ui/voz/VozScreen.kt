package com.gv.app.ui.voz

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.domain.model.VozKind
import com.gv.app.domain.model.VozSuggestResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import com.gv.app.voice.SpeechToText
import kotlinx.coroutines.launch

@Composable
fun VozScreen(vm: VozViewModel = viewModel()) {
    val ctx = LocalContext.current
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()

    val state by vm.state.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.loadUsage() }
    LaunchedEffect(vm) { vm.toast.collect { snackbar.showSnackbar(it) } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startDictation()
        else scope.launch { snackbar.showSnackbar("Permiso de micrófono denegado") }
    }
    fun onMicTap() {
        if (listening) {
            vm.stopDictation()
            return
        }
        if (!SpeechToText.isAvailable(ctx)) {
            scope.launch { snackbar.showSnackbar("Reconocimiento de voz no disponible en el dispositivo") }
            return
        }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.startDictation() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize().background(GvColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            AiSpendCard(usage)

            InputCard(
                input = input,
                listening = listening,
                busy = state is VozUiState.Loading || state is VozUiState.Executing,
                onInputChange = vm::onInputChange,
                onMicTap = ::onMicTap,
                onSubmit = vm::submit,
            )

            when (val s = state) {
                is VozUiState.Idle -> Unit
                is VozUiState.Loading -> LoadingRow("Pensando…")
                is VozUiState.Executing -> LoadingRow("Ejecutando…")
                is VozUiState.Suggestion -> SuggestionCard(
                    data = s.data,
                    onApprove = vm::approve,
                    onFeedback = vm::giveFeedback,
                )
                is VozUiState.Result -> ResultCard(s, onNew = vm::reset)
                is VozUiState.Rejected -> InfoCard(
                    title = "No pude interpretarlo",
                    body = s.reason,
                    tone = GvColors.Warning,
                )
                is VozUiState.Error -> InfoCard(
                    title = "Algo falló",
                    body = s.message,
                    tone = GvColors.Danger,
                    actionLabel = "Reintentar",
                    onAction = vm::submit,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(snackbarData = data, containerColor = GvColors.Surface, contentColor = GvColors.Text)
        }
    }
}

@Composable
private fun InputCard(
    input: String,
    listening: Boolean,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onMicTap: () -> Unit,
    onSubmit: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pregunta o instrucción…", color = GvColors.TextMuted) },
                minLines = 2,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    cursorColor = GvColors.Primary,
                    focusedContainerColor = GvColors.BgLight,
                    unfocusedContainerColor = GvColors.BgLight,
                ),
            )
            Spacer(Modifier.width(spacing.md))
            FilledIconButton(
                onClick = onMicTap,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (listening) GvColors.Danger else GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
            ) {
                Icon(
                    imageVector = if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (listening) "Detener" else "Dictar",
                )
            }
        }
        Button(
            onClick = onSubmit,
            enabled = input.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
        ) {
            Text("Generar consulta")
        }
    }
}

@Composable
private fun SuggestionCard(
    data: VozSuggestResponse,
    onApprove: () -> Unit,
    onFeedback: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(data.explanation, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)

        if (data.query.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(GvColors.Surface)
                    .padding(spacing.md),
            ) {
                Text(
                    data.query,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = GvColors.TextMuted,
                )
            }
        }
        data.warning?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = GvColors.Warning)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
            ) { Text("Aprobar") }
            TextButton(onClick = onFeedback) {
                Text("Dar feedback", color = GvColors.TextMuted)
            }
        }
    }
}

@Composable
private fun ResultCard(result: VozUiState.Result, onNew: () -> Unit) {
    val spacing = LocalSpacing.current
    val isWrite = result.kind == VozKind.WRITE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, if (isWrite) GvColors.Success else GvColors.BorderLight, RoundedCornerShape(12.dp))
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (isWrite) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = GvColors.Success)
                Spacer(Modifier.width(spacing.sm))
                Text("Hecho", style = MaterialTheme.typography.titleSmall, color = GvColors.Success, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(result.summary, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
        result.rowCount?.let {
            Text("$it fila(s)", style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
        }
        TextButton(onClick = onNew) { Text("Nueva consulta", color = GvColors.Primary) }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    tone: androidx.compose.ui.graphics.Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GvColors.BgLight)
            .border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = tone, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = GvColors.Primary) }
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = GvColors.Primary, strokeWidth = 2.dp, modifier = Modifier.height(20.dp).width(20.dp))
        Spacer(Modifier.width(spacing.md))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
    }
}
