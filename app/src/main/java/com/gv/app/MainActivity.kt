package com.gv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gv.app.data.api.RetrofitClient
import com.gv.app.data.local.TokenManager
import com.gv.app.ui.navigation.AppNavigation
import com.gv.app.ui.theme.GvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RetrofitClient.tokenManager = TokenManager(applicationContext)

        setContent {
            GvTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
