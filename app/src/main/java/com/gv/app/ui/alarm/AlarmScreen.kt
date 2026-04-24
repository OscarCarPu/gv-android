package com.gv.app.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(vm: AlarmViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

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
            .padding(horizontal = spacing.xxl, vertical = spacing.huge),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = "Alarm",
            style = MaterialTheme.typography.displaySmall,
            color = GvColors.Text,
        )

        Text(
            text = "Boom Boom — Spotify",
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )

        Spacer(Modifier.fillMaxWidth())

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
    }
}
