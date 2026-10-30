package com.moblized.nutritrack.data

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow

/** A published NutriTrack build on GitHub Releases. */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    /** What changed, from the release notes. */
    val notes: String,
    /** The APK (Android) or IPA (iPhone) to install. */
    val apkUrl: String?,
    val apkBytes: Long,
    /** The release page, for downloading in the browser. */
    val pageUrl: String,
)

/**
 * Update checks against the latest GitHub release. Android downloads and installs the APK in the app;
 * on iPhone the release's IPA is installed through SideStore.
 */
object Updates {
    const val REPO = "moblized/moblized-nutritrack"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    /** A newer release to offer, shown as a dialog over the app. */
    val available = MutableStateFlow<AppRelease?>(null)

    /** "v1.0.21" -> 21 (builds are numbered by their CI run, which is also the version code). */
    fun versionCodeOf(tag: String): Int? = Regex("""(\d+)\s*$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Parses GitHub's release JSON; null for anything that isn't a usable release. */
    fun parseRelease(json: String, assetExtension: String = "apk"): AppRelease? {
        val o = parseObject(json) ?: return null
        if (o.bool("draft") || o.bool("prerelease")) return null
        val tag = o.str("tag_name")
        val code = versionCodeOf(tag) ?: return null
        val asset = o.arr("assets")?.objects().orEmpty()
            .firstOrNull { it.str("name").endsWith(".$assetExtension", ignoreCase = true) }
        val notes = o.str("body")
            .lines()
            .filterNot { it.startsWith("Co-Authored-By:") || it.startsWith("Claude-Session:") }
            .joinToString("\n")
            .trim()
        return AppRelease(
            versionCode = code,
            versionName = tag.removePrefix("v"),
            notes = notes,
            apkUrl = asset?.str("browser_download_url")?.takeIf { it.startsWith("https://") },
            apkBytes = asset?.long("size") ?: 0L,
            pageUrl = o.str("html_url").ifBlank { "https://github.com/$REPO/releases/latest" },
        )
    }

    suspend fun fetchLatest(assetExtension: String): AppRelease? {
        val r = http.get(LATEST) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "NutriTrack")
        }
        if (r.status.value !in 200..299) throw IllegalStateException("GitHub returned HTTP ${r.status.value}")
        return parseRelease(r.bodyAsText(), assetExtension)
    }

    /** Called when the app opens or comes back: offers a newer release unless it was put off recently. */
    suspend fun checkIfDue() {
        val graph = Graph.app
        val updater = graph.platform.updater
        val state = graph.settings.updateState()
        val now = nowMillis()
        if (now - state.checkedAt < CHECK_EVERY_MS) return
        val latest = runCatching { fetchLatest(updater.assetExtension) }.getOrNull() ?: return
        graph.settings.setUpdateChecked(now)
        if (latest.versionCode <= updater.installedVersionCode || latest.apkUrl == null) return
        val snoozed = state.snoozedCode == latest.versionCode && now < state.snoozedUntil
        if (!snoozed) available.value = latest
    }

    /** "Check for updates" in Settings: returns the newer release (and offers it), or null when up to date. */
    suspend fun checkNow(): AppRelease? {
        val graph = Graph.app
        val updater = graph.platform.updater
        val latest = fetchLatest(updater.assetExtension) ?: return null
        graph.settings.setUpdateChecked(nowMillis())
        if (latest.versionCode <= updater.installedVersionCode || latest.apkUrl == null) return null
        available.value = latest
        return latest
    }

    /** "Later": don't offer this version again for a day. */
    suspend fun snooze(release: AppRelease) {
        Graph.app.settings.snoozeUpdate(release.versionCode, nowMillis() + SNOOZE_MS)
        available.value = null
    }
}
