package com.moblized.nutritrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.ThemeMode
import com.moblized.nutritrack.ui.theme.NutriTheme

/**
 * The whole app, the same on Android and iPhone. [onDarkChange] lets Android swap its launcher icon;
 * [startRoute] opens another tab first (used for iPhone simulator screenshots).
 */
@Composable
fun NutriTrackApp(onDarkChange: (Boolean) -> Unit = {}, startRoute: String = "diary") {
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
        LaunchedEffect(dark) { onDarkChange(dark) }
        NutriTheme(dark = dark, pureBlack = settings.pureBlack) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AppNav(settings, startRoute)
            }
        }
    }
}
