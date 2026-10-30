package com.moblized.nutritrack.data

import androidx.compose.runtime.Composable

/**
 * What each phone does its own way. Android implements it in the app module (Health Connect, ML Kit,
 * WorkManager, the widget, APK updates); the iPhone app in iosMain.
 */
interface PlatformServices {
    val health: HealthService
    val updater: AppUpdater

    /** The evening low-nutrient notification; null where it isn't available yet. */
    val alerts: AlertScheduler?

    /** False when the phone's animations are turned off (confetti is skipped). */
    val animationsEnabled: Boolean get() = true

    /** Called after the diary changes so a home-screen widget can redraw. */
    fun refreshWidget() {}

    /** A readable name for the app that recorded a weigh-in. */
    fun appLabel(packageName: String): String = packageName

    /** Extra Settings sections this phone has (Health Connect and the smart scale on Android). */
    @Composable
    fun HealthSettings(settings: Settings, showMessage: (String) -> Unit) {
    }

    /** Returns a function that starts a barcode scan, or null when scanning isn't possible. */
    @Composable
    fun rememberBarcodeScanner(onCode: (String) -> Unit, onError: (String) -> Unit): (() -> Unit)? = null

    /** Returns a function that asks to show notifications (when needed) and reports whether it may. */
    @Composable
    fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }
}

/** Syncing with the phone's health app. */
interface HealthService {
    suspend fun isAvailable(): Boolean = false
    suspend fun upsert(entries: List<DiaryEntry>): Int = 0
    suspend fun delete(entryId: Long) {}
    suspend fun latestWeighIn(): WeighIn? = null
}

object NoHealth : HealthService

data class WeighIn(val kg: Double, val timeMillis: Long, val sourcePackage: String)

interface AlertScheduler {
    fun schedule(minutes: Int, replace: Boolean)
    fun cancel()
}

/** Installing a newer release: in the app on Android, through SideStore on iPhone. */
interface AppUpdater {
    /** Which release file is for this phone: "apk" or "ipa". */
    val assetExtension: String
    val installedVersionCode: Int
    val installedVersionName: String

    /** True when the app downloads and installs updates itself (Android). */
    val installsInApp: Boolean

    /** Downloads the release file and returns its local path. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): String =
        throw UnsupportedOperationException("Updates are installed outside the app on this phone")

    fun canInstall(): Boolean = false
    fun openInstallPermission() {}
    fun install(path: String) {}
}
