package com.gv.app.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gv.app.AppColors
import com.gv.app.ui.components.SummaryProgressBar
import com.gv.app.ui.components.TimeDisplay

@Composable
fun TimerWidget(vm: TimerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Task name (read-only, assigned via play button on task rows)
            Text(
                text = state.selectedTaskName.ifEmpty { "Sin tarea seleccionada" },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.selectedTaskName.isEmpty()) AppColors.muted
                        else MaterialTheme.colorScheme.onSurface
            )

            // Timer display + controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeDisplay(seconds = state.elapsedSeconds)

                if (state.isRunning) {
                    FilledTonalButton(
                        onClick = { vm.stopTimer() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.danger.copy(alpha = 0.2f),
                            contentColor = AppColors.danger
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Detener")
                        Spacer(Modifier.width(4.dp))
                        Text("Detener")
                    }
                } else {
                    FilledTonalButton(
                        onClick = { vm.startTimer() },
                        enabled = state.selectedTaskId != null,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.success.copy(alpha = 0.2f),
                            contentColor = AppColors.success
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Iniciar")
                        Spacer(Modifier.width(4.dp))
                        Text("Iniciar")
                    }
                }
            }

            // Comment
            OutlinedTextField(
                value = state.comment,
                onValueChange = { vm.updateComment(it) },
                label = { Text("Comentario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Summary progress bars
            state.summary?.let { summary ->
                SummaryProgressBar(
                    current = summary.today,
                    target = 12 * 3600L,
                    label = "Hoy"
                )
                SummaryProgressBar(
                    current = summary.week,
                    target = 90 * 3600L,
                    label = "Semana"
                )
            }
        }
    }
}
