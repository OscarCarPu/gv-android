package com.gv.app.ui.otros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.ui.alarm.AlarmScreen
import com.gv.app.ui.rutas.RutasScreen
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

private enum class OtrosTab(val label: String) {
    ROUTES("Rutas"), ALARM("Alarma")
}

/**
 * Groups the secondary features under one bottom-tab. Uses a plain `when` body
 * (not a pager) so each sub-screen is disposed when you leave it.
 */
@Composable
fun OtrosScreen() {
    var tab by rememberSaveable { mutableStateOf(OtrosTab.ROUTES) }
    Column(Modifier.fillMaxSize().background(GvColors.Bg)) {
        TabBar(selected = tab, onSelect = { tab = it })
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                OtrosTab.ROUTES -> RutasScreen()
                OtrosTab.ALARM -> AlarmScreen()
            }
        }
    }
}

@Composable
private fun TabBar(selected: OtrosTab, onSelect: (OtrosTab) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GvColors.BgLight)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        OtrosTab.entries.forEach { t ->
            val active = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) GvColors.Primary else GvColors.BorderLight,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) GvColors.Primary else GvColors.TextMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
