package com.moblized.nutritrack.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A published NutriTrack build on GitHub Releases. */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    /** What changed, from the release notes. */
    val notes: String,
    val apkUrl: String?,
    val apkBytes: Long,
    /** The release page, for downloading in the browser if the in-app download fails. */
    val pageUrl: String,
)

/**
 * In-app updates: checks the latest GitHub release, downloads its APK and hands it to Android's installer.
 * Release builds are signed with the same key, so each one installs over the last and keeps your data.
 */
object Updates {
    const val REPO = "moblized/moblized-nutritrack"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val SNOOZE_MS = 24 * 60 * 60 * 1000L
    private const val PREFS = "updates"

    /** A newer release to offer, shown as a dialog over the app. */
    val available = MutableStateFlow<AppRelease?>(null)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun installedVersionCode(context: Context): Int = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    }.getOrDefault(0)

    fun installedVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /** "v1.0.21" -> 21 (builds are numbered by their CI run, which is also the versionCode). */
    fun versionCodeOf(tag: String): Int? = Regex("""(\d+)\s*$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Parses GitHub's release JSON; null for anything that isn't a usable release. */
    fun parseRelease(json: String): AppRelease? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name")
        val code = versionCodeOf(tag) ?: return null
        val assets = o.optJSONArray("assets")
        var apkUrl: String? = null
        var apkBytes = 0L
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url").takeIf { it.startsWith("https://") }
                apkBytes = a.optLong("size")
                break
            }
        }
        val notes = o.optString("body").takeIf { !o.isNull("body") }.orEmpty()
            .lines()
            .filterNot { it.startsWith("Co-Authored-By:") || it.startsWith("Claude-Session:") }
            .joinToString("\n")
            .trim()
        return AppRelease(
            versionCode = code,
            versionName = tag.removePrefix("v"),
            notes = notes,
            apkUrl = apkUrl,
            apkBytes = apkBytes,
            pageUrl = o.optString("html_url").ifBlank { "https://github.com/$REPO/releases/latest" },
        )
    }

    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NutriTrack")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub returned HTTP ${r.code}")
            parseRelease(r.body?.string().orEmpty())
        }
    }

    /** Called when the app opens or comes back: offers a newer release unless it was put off recently. */
    suspend fun checkIfDue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checked", 0L) < CHECK_EVERY_MS) return
        val latest = runCatching { fetchLatest() }.getOrNull() ?: return
        prefs.edit().putLong("checked", now).apply()
        if (latest.versionCode <= installedVersionCode(context)) return
        val snoozed = prefs.getInt("snoozed_code", 0) == latest.versionCode && now < prefs.getLong("snoozed_until", 0L)
        if (!snoozed) available.value = latest
    }

    /** "Check for updates" in Settings: returns the newer release (and offers it), or null when up to date. */
    suspend fun checkNow(context: Context): AppRelease? {
        val latest = fetchLatest() ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("checked", System.currentTimeMillis()).apply()
        if (latest.versionCode <= installedVersionCode(context)) return null
        available.value = latest
        return latest
    }

    /** "Later": don't offer this version again for a day. */
    fun snooze(context: Context, release: AppRelease) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("snoozed_code", release.versionCode)
            .putLong("snoozed_until", System.currentTimeMillis() + SNOOZE_MS)
            .apply()
        available.value = null
    }

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
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
        out
    }

    /** Android asks once whether NutriTrack may install apps; true when it already may. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for NutriTrack. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the downloaded APK to Android's installer, which asks to confirm the update. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openReleasePage(context: Context, release: AppRelease) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
