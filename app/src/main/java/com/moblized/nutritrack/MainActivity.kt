package com.moblized.nutritrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Updates
import com.moblized.nutritrack.ui.NutriTrackApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The launcher icon follows the app's light/dark mode.
            NutriTrackApp(onDarkChange = { dark -> IconSwitcher.apply(applicationContext, dark) })
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up a new weigh-in from the smart scale (Health Connect only allows reads in the foreground).
        lifecycleScope.launch { runCatching { Graph.app.syncWeight() } }
        // Offer a newer release when one is out (checked at most every few hours).
        lifecycleScope.launch { runCatching { Updates.checkIfDue() } }
    }

    override fun onStop() {
        super.onStop()
        Graph.app.refreshWidget()
    }
}
