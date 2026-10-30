package com.moblized.nutritrack

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings.Global
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.moblized.nutritrack.alerts.LowNutrientAlerts
import com.moblized.nutritrack.data.AlertScheduler
import com.moblized.nutritrack.data.AppUpdater
import com.moblized.nutritrack.data.HealthService
import com.moblized.nutritrack.data.HealthSync
import com.moblized.nutritrack.data.PlatformServices
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The Android side of the shared app: Health Connect, ML Kit scanning, the widget, alerts and APK updates. */
class AndroidPlatform(private val context: Context) : PlatformServices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val healthSync = HealthSync(context)

    override val health: HealthService = healthSync
    override val updater: AppUpdater = AndroidUpdater(context)

    override val alerts: AlertScheduler = object : AlertScheduler {
        override fun schedule(minutes: Int, replace: Boolean) = LowNutrientAlerts.schedule(context, minutes, replace)
        override fun cancel() = LowNutrientAlerts.cancel(context)
    }

    override val animationsEnabled: Boolean
        get() = runCatching { Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)

    override fun refreshWidget() {
        scope.launch { runCatching { TodayWidget().updateAll(context) } }
    }

    override fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    @Composable
    override fun HealthSettings(settings: Settings, showMessage: (String) -> Unit) {
        AndroidHealthSettings(healthSync, settings, showMessage)
    }

    @Composable
    override fun rememberBarcodeScanner(onCode: (String) -> Unit, onError: (String) -> Unit): (() -> Unit)? {
        val ctx = LocalContext.current
        val code by rememberUpdatedState(onCode)
        val error by rememberUpdatedState(onError)
        return remember(ctx) {
            val start: () -> Unit = {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                    .build()
                GmsBarcodeScanning.getClient(ctx, options).startScan()
                    .addOnSuccessListener { barcode -> barcode.rawValue?.let { code(it) } }
                    .addOnFailureListener { e -> error(e.message ?: "unknown error") }
            }
            start
        }
    }

    @Composable
    override fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
        val ctx = LocalContext.current
        val result by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> result(granted) }
        return {
            if (Build.VERSION.SDK_INT >= 33 && !LowNutrientAlerts.canNotify(ctx)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                result(true)
            }
        }
    }
}
