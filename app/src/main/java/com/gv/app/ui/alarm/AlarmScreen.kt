package com.gv.app.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gv.app.BuildConfig
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(vm: AlarmViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    var showPicker by remember { mutableStateOf(false) }

    val timeState = rememberTimePickerState(
        initialHour = config.hour,
        initialMinute = config.minute,
        is24Hour = true,
    )

    LaunchedEffect(timeState.hour, timeState.minute) {
        if (timeState.hour != config.hour || timeState.minute != config.minute) {
            vm.onTimeChanged(timeState.hour, timeState.minute)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xxl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GvColors.BgLight, shape = RoundedCornerShape(12.dp))
                .border(1.dp, GvColors.BorderLight, shape = RoundedCornerShape(12.dp))
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = "Time",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = timeState)
            }

            HorizontalDivider(color = GvColors.Border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.titleLarge,
                    color = GvColors.Text,
                )
                Switch(
                    checked = config.enabled,
                    onCheckedChange = vm::onEnabledToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = GvColors.Primary,
                        checkedBorderColor = GvColors.Primary,
                        uncheckedThumbColor = GvColors.TextMuted,
                        uncheckedTrackColor = GvColors.BgLight,
                        uncheckedBorderColor = GvColors.BorderLight,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GvColors.BgLight, shape = RoundedCornerShape(12.dp))
                .border(1.dp, GvColors.BorderLight, shape = RoundedCornerShape(12.dp))
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GvColors.Bg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (config.playlistImageUrl != null) {
                        AsyncImage(
                            model = config.playlistImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = config.playlistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = GvColors.Text,
                    )
                    Text(
                        text = config.playlistUri,
                        style = MaterialTheme.typography.bodySmall,
                        color = GvColors.TextMuted,
                    )
                }
            }

            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change")
            }
        }

        if (BuildConfig.DEBUG) {
            Button(
                onClick = vm::onTestAlarm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary),
            ) {
                Text("Test alarm now")
            }
        }
    }

    if (showPicker) {
        PlaylistPickerDialog(
            vm = vm,
            onDismiss = { showPicker = false },
        )
    }
}
