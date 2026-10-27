package com.moblized.nutritrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.ui.theme.NutriTheme

/** Shown by Health Connect when the user asks why NutriTrack wants nutrition access. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriTheme(dark = isSystemInDarkTheme(), pureBlack = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                        Text("How NutriTrack uses Health Connect", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "NutriTrack writes nutrition data: each food you log (calories, macros, vitamins and " +
                                "minerals) is saved to Health Connect so apps like Samsung Health can show it. " +
                                "If you turn on scale sync, it also reads your latest weight so your calorie and protein " +
                                "targets follow it. It reads nothing else, and your health data never leaves your phone.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { finish() }) { Text("Got it") }
                    }
                }
            }
        }
    }
}
