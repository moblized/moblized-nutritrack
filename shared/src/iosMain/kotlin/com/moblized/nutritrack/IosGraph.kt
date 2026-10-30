package com.moblized.nutritrack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.moblized.nutritrack.data.AlertScheduler
import com.moblized.nutritrack.data.AppGraph
import com.moblized.nutritrack.data.AppUpdater
import com.moblized.nutritrack.data.Food
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.HealthService
import com.moblized.nutritrack.data.NoHealth
import com.moblized.nutritrack.data.PlatformServices
import com.moblized.nutritrack.data.SETTINGS_FILE
import com.moblized.nutritrack.data.UserDatabase
import com.moblized.nutritrack.data.now
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDomainMask

/** Builds the shared app graph with iPhone storage locations. */
object IosGraph {
    private var started = false

    /** Tab to open first (CI screenshots), from the `-NTTab` launch argument. */
    var startRoute: String = "diary"
        private set

    fun start(scanner: BarcodeScannerBridge) {
        if (started) return
        started = true
        val docs = documentsDirectory()
        Graph.app = AppGraph(
            platform = IosPlatform(scanner),
            database = Room.databaseBuilder<UserDatabase>(name = "$docs/nutritrack.db"),
            usdaPath = { NSBundle.mainBundle.pathForResource("usda", ofType = "db") ?: error("usda.db is missing from the app") },
            settingsStore = PreferenceDataStoreFactory.createWithPath(produceFile = { "$docs/$SETTINGS_FILE".toPath() }),
        )
        val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
        args.indexOf("-NTTab").takeIf { it >= 0 }?.let { i -> args.getOrNull(i + 1)?.let { startRoute = it } }
        if ("-NTDemo" in args) seedDemoDay()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun documentsDirectory(): String {
        val url = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
        return requireNotNull(url?.path) { "No documents directory" }
    }

    /** A sample day for simulator screenshots in CI. */
    private fun seedDemoDay() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val app = Graph.app
            val today = LocalDate.now()
            if (app.db.diary().day(today.toString()).isNotEmpty()) return@launch
            val picks = listOf(
                0 to "Egg, whole, raw, fresh", 0 to "Bananas, raw", 0 to "Oats, raw",
                1 to "Chicken, breast, boneless, skinless, raw", 1 to "Spinach, raw",
                3 to "Yogurt, Greek, plain, nonfat", 3 to "Nuts, almonds",
            )
            for ((meal, name) in picks) {
                val hit = app.usda.search(name).firstOrNull { it.name == name } ?: app.usda.search(name).firstOrNull() ?: continue
                val food: Food = app.usda.food(hit.id) ?: continue
                app.saveEntry(0, today, meal, food, food.defaultQuantity(), food.defaultUnit())
            }
        }
    }
}

private class IosPlatform(private val scanner: BarcodeScannerBridge) : PlatformServices {
    override val health: HealthService = NoHealth
    override val updater: AppUpdater = IosUpdater()

    // Evening notifications aren't on iPhone yet.
    override val alerts: AlertScheduler? = null

    @Composable
    override fun rememberBarcodeScanner(onCode: (String) -> Unit, onError: (String) -> Unit): (() -> Unit)? {
        val code by rememberUpdatedState(onCode)
        val error by rememberUpdatedState(onError)
        return remember {
            val start: () -> Unit = { scanner.scan(onResult = { c -> c?.let { code(it) } }, onError = { error(it) }) }
            start
        }
    }
}

/** iPhone updates come through SideStore; this just reads the installed version. */
private class IosUpdater : AppUpdater {
    override val assetExtension = "ipa"
    override val installsInApp = false
    override val installedVersionCode: Int
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 0
    override val installedVersionName: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
}
