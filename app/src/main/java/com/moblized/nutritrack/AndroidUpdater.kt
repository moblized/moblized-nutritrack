package com.moblized.nutritrack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.moblized.nutritrack.data.AppRelease
import com.moblized.nutritrack.data.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads a release APK and hands it to Android's installer. Release builds share one signing key,
 * so each installs over the last and keeps the diary.
 */
class AndroidUpdater(private val context: Context) : AppUpdater {
    override val assetExtension = "apk"
    override val installsInApp = true

    override val installedVersionCode: Int
        get() = runCatching {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
        }.getOrDefault(0)

    override val installedVersionName: String
        get() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw IOException("This release has no APK attached")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever keep the one being installed
        val out = File(dir, "NutriTrack-${release.versionName}.apk")
        val req = Request.Builder().url(url).header("User-Agent", "NutriTrack").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
            val body = r.body ?: throw IOException("Download failed (empty response)")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        out.absolutePath
    }

    /** Android asks once whether NutriTrack may install apps; true when it already may. */
    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun install(path: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", File(path))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
