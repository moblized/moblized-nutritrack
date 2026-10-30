package com.moblized.nutritrack.data

import android.app.Application
import com.moblized.nutritrack.ui.releaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric for the real org.json; no network here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdatesTest {
    private val json = """
        {"tag_name":"v1.0.21","name":"NutriTrack 1.0.21","draft":false,"prerelease":false,
         "html_url":"https://github.com/moblized/moblized-nutritrack/releases/tag/v1.0.21",
         "body":"Add an update prompt\n\n- Checks GitHub for new releases\n  when the app opens\n\nCo-Authored-By: Someone <x@y.z>\nClaude-Session: https://example.com",
         "assets":[{"name":"NutriTrack-1.0.21.apk","size":41234567,
                    "browser_download_url":"https://github.com/moblized/moblized-nutritrack/releases/download/v1.0.21/NutriTrack-1.0.21.apk"}]}
    """.trimIndent()

    @Test
    fun readsTheLatestRelease() {
        val r = Updates.parseRelease(json)!!
        assertEquals(21, r.versionCode)
        assertEquals("1.0.21", r.versionName)
        assertEquals(41234567L, r.apkBytes)
        assertEquals("https://github.com/moblized/moblized-nutritrack/releases/download/v1.0.21/NutriTrack-1.0.21.apk", r.apkUrl)
        assertEquals("Add an update prompt\n\n- Checks GitHub for new releases\n  when the app opens", r.notes)
        assertEquals("Add an update prompt\n• Checks GitHub for new releases when the app opens", releaseNotes(r.notes))
        assertEquals("Fixes and improvements.", releaseNotes(""))
    }

    @Test
    fun ignoresPrereleasesAndOddTags() {
        assertNull(Updates.parseRelease(json.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(Updates.parseRelease("{\"tag_name\":\"screenshots\"}"))
        assertNull(Updates.parseRelease("not json"))
        assertEquals(20, Updates.versionCodeOf("v1.0.20"))
        assertEquals(7, Updates.versionCodeOf("1.0.7"))
    }
}
