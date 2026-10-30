package com.moblized.nutritrack

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings.Global
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.moblized.nutritrack.alerts.LowNutrientAlerts
import com.moblized.nutritrack.data.AlertScheduler
import com.moblized.nutritrack.data.AppUpdater
import com.moblized.nutritrack.data.HealthService
import com.moblized.nutritrack.data.HealthSync
import com.moblized.nutritrack.data.OcrLine
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
    override fun rememberReceiptScanner(
        onReading: () -> Unit,
        onLines: (List<OcrLine>) -> Unit,
        onError: (String) -> Unit,
    ): (() -> Unit)? {
        val activity = LocalContext.current.findActivity()
        val reading by rememberUpdatedState(onReading)
        val lines by rememberUpdatedState(onLines)
        val error by rememberUpdatedState(onError)
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages.orEmpty().map { it.imageUri }
            if (pages.isEmpty()) return@rememberLauncherForActivityResult
            reading()
            scope.launch {
                try {
                    lines(ReceiptOcr.read(context, pages))
                } catch (e: Exception) {
                    error(e.message ?: "unknown error")
                }
            }
        }
        if (activity == null) return null
        return remember(activity) {
            val start: () -> Unit = {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true) // a screenshot of an online order works too
                    .setPageLimit(5) // long receipts in parts
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
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

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
