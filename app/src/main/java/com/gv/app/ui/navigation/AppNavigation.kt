package com.gv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gv.app.data.api.RetrofitClient
import com.gv.app.ui.alarm.AlarmScreen
import com.gv.app.ui.login.LoginScreen

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}

@Composable
fun AppNavigation() {
    val tokenManager = RetrofitClient.tokenManager
    val token by tokenManager.tokenFlow.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val start = remember { if (tokenManager.tokenFlow.value != null) Routes.HOME else Routes.LOGIN }

    LaunchedEffect(token) {
        val target = if (token != null) Routes.HOME else Routes.LOGIN
        val current = navController.currentDestination?.route
        if (current != null && current != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) { LoginScreen() }
        composable(Routes.HOME) { AlarmScreen() }
    }
}
