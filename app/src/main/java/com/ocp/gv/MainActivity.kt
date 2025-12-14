package com.ocp.gv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ocp.gv.ui.screens.HabitListScreen
import com.ocp.gv.ui.theme.GvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GvTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HabitListScreen()
                }
            }
        }
    }
}
