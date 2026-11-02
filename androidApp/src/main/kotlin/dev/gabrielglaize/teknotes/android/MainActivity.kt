package dev.gabrielglaize.teknotes.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrielglaize.teknotes.Appearance
import dev.gabrielglaize.teknotes.android.ui.AppViewModel
import dev.gabrielglaize.teknotes.android.ui.TeknotesRoot
import dev.gabrielglaize.teknotes.android.ui.TeknotesTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        // Back in the app: whatever was waiting for the network gets another chance.
        (application as TeknotesApplication).teknotes.sync.schedule()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val teknotes = (application as TeknotesApplication).teknotes
        setContent {
            val app = viewModel { AppViewModel(teknotes) }
            val preferences by app.preferences.collectAsStateWithLifecycle()
            val dark = when (preferences.appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.DARK -> true
                Appearance.LIGHT -> false
            }
            // Status bar icons follow the chosen appearance, not the phone's setting.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TeknotesTheme(dark = dark) {
                TeknotesRoot(teknotes)
            }
        }
    }
}
