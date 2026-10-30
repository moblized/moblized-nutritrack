package com.moblized.nutritrack

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.moblized.nutritrack.data.Updates
import com.moblized.nutritrack.ui.NutriTrackApp
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * The iPhone app's root screen, created from Swift with the camera scanner it provides.
 * Launch arguments for CI screenshots: `-NTDemo` fills today's diary, `-NTTab <route>` opens a tab.
 */
fun MainViewController(scanner: BarcodeScannerBridge): UIViewController {
    IosGraph.start(scanner)
    return ComposeUIViewController {
        val scope = rememberCoroutineScope()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            scope.launch { runCatching { Updates.checkIfDue() } }
        }
        NutriTrackApp(startRoute = IosGraph.startRoute)
    }
}

/** Implemented in Swift (AVFoundation) and passed in when the app starts. */
interface BarcodeScannerBridge {
    /** Opens the camera; [onResult] gets the barcode, or null when the person cancels. */
    fun scan(onResult: (String?) -> Unit, onError: (String) -> Unit)
}
