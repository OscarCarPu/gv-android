package com.gv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gv.app.data.api.RetrofitClient
import com.gv.app.data.local.TokenManager
import com.gv.app.ui.navigation.AppNavigation

private val GvColorScheme = darkColorScheme(
    primary      = Color(0xFF3B82F6),
    secondary    = Color(0xFF8B5CF6),
    surface      = Color(0xFF1E293B),
    background   = Color(0xFF0F172A),
    onSurface    = Color(0xFFF1F5F9),
    onBackground = Color(0xFFF1F5F9),
)

object AppColors {
    val success   = Color(0xFF22C55E)
    val warning   = Color(0xFFEAB308)
    val danger    = Color(0xFFEF4444)
    val muted     = Color(0xFF94A3B8)
    val secondary = Color(0xFF8B5CF6)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RetrofitClient.tokenManager = TokenManager(applicationContext)

        setContent {
            MaterialTheme(colorScheme = GvColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
