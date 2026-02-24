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
import com.gv.app.ui.habits.HabitsScreen

private val GvColorScheme = darkColorScheme(
    primary      = Color(0xFF3B82F6),   // gv-web --color-primary
    surface      = Color(0xFF1E293B),   // gv-web --color-bg-light (cards)
    background   = Color(0xFF0F172A),   // gv-web --color-bg
    onSurface    = Color(0xFFF1F5F9),   // gv-web --color-text
    onBackground = Color(0xFFF1F5F9),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = GvColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HabitsScreen()
                }
            }
        }
    }
}
