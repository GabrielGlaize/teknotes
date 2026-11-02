package dev.gabrielglaize.teknotes.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.gabrielglaize.teknotes.android.ui.TeknotesRoot
import dev.gabrielglaize.teknotes.android.ui.TeknotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val teknotes = (application as TeknotesApplication).teknotes
        setContent {
            TeknotesTheme {
                TeknotesRoot(teknotes)
            }
        }
    }
}
