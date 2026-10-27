package com.moblized.nutritrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.ThemeMode
import com.moblized.nutritrack.ui.AppNav
import com.moblized.nutritrack.ui.theme.NutriTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val loaded: Settings? by Graph.app.settings.flow.collectAsState(initial = null)
            val settings = loaded
            val systemDark = isSystemInDarkTheme()
            if (settings == null) {
                NutriTheme(dark = systemDark, pureBlack = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            } else {
                val dark = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                LaunchedEffect(dark) { IconSwitcher.apply(applicationContext, dark) }
                NutriTheme(dark = dark, pureBlack = settings.pureBlack) {
                    AppNav(settings)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Graph.app.refreshWidget()
    }
}
