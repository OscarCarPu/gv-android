package com.gv.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.gv.app.ui.alarm.AlarmScreen
import com.gv.app.ui.habits.HabitsScreen
import com.gv.app.ui.theme.GvColors

private enum class HomeTab(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
) {
    ALARM("Alarm", Icons.Outlined.Alarm, enabled = true),
    HABITS("Habits", Icons.Outlined.CheckCircle, enabled = true),
    TASKS("Tasks", Icons.AutoMirrored.Outlined.List, enabled = false),
    FINANCE("Finance", Icons.Outlined.AccountBalanceWallet, enabled = false),
}

@Composable
fun HomeScreen() {
    var selected by rememberSaveable { mutableStateOf(HomeTab.ALARM) }

    Scaffold(
        containerColor = GvColors.Bg,
        bottomBar = { GvNavigationBar(selected = selected, onSelect = { selected = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selected) {
                HomeTab.ALARM -> AlarmScreen()
                HomeTab.HABITS -> HabitsScreen()
                HomeTab.TASKS, HomeTab.FINANCE -> Unit
            }
        }
    }
}

@Composable
private fun GvNavigationBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
) {
    NavigationBar(
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                enabled = tab.enabled,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GvColors.Primary,
                    selectedTextColor = GvColors.Primary,
                    indicatorColor = GvColors.Primary.copy(alpha = 0.10f),
                    unselectedIconColor = GvColors.TextMuted,
                    unselectedTextColor = GvColors.TextMuted,
                    disabledIconColor = GvColors.TextMuted.copy(alpha = 0.40f),
                    disabledTextColor = GvColors.TextMuted.copy(alpha = 0.40f),
                ),
            )
        }
    }
}
