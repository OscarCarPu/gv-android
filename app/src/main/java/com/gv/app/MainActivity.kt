package com.gv.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.gv.app.notification.NotificationHelper
import com.gv.app.notification.NotificationScheduler
import com.gv.app.ui.habits.HabitsScreen

private val GvColorScheme = darkColorScheme(
    primary      = Color(0xFF3B82F6),
    surface      = Color(0xFF1E293B),
    background   = Color(0xFF0F172A),
    onSurface    = Color(0xFFF1F5F9),
    onBackground = Color(0xFFF1F5F9),
)

class MainActivity : ComponentActivity() {

    /**
     * Drives whether HabitsScreen should auto-open the wizard.
     * Declared as Compose state so a change here triggers recomposition automatically.
     * onNewIntent() flips this to true when a notification is tapped while the app is open.
     */
    private var openWizard by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannel(this)
        NotificationScheduler(applicationContext).scheduleIfNotAlreadyScheduled()
        requestNotificationPermission()

        // Only read the intent extra on a real fresh launch, not on rotation/recreation.
        // Without this guard, the wizard would reopen on every screen rotation.
        if (savedInstanceState == null) {
            openWizard = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_WIZARD, false)
        }

        setContent {
            MaterialTheme(colorScheme = GvColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HabitsScreen(openWizard = openWizard)
                }
            }
        }
    }

    /**
     * Called when a new intent arrives while this Activity is already on top of the stack.
     * Requires android:launchMode="singleTop" in the manifest.
     * Handles notification taps while the app is already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_WIZARD, false)) {
            openWizard = true
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
