package com.moblized.nutritrack

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.moblized.nutritrack.data.AppGraph
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.SETTINGS_FILE
import com.moblized.nutritrack.data.UsdaDatabase
import com.moblized.nutritrack.data.UserDatabase
import okio.Path.Companion.toPath
import java.io.File

class NutriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.app = AppGraph(
            platform = AndroidPlatform(this),
            // Same files the app has always used, so diaries and settings carry over.
            database = Room.databaseBuilder<UserDatabase>(this, getDatabasePath("nutritrack.db").absolutePath),
            usdaPath = { usdaFile(this) },
            settingsStore = PreferenceDataStoreFactory.createWithPath(
                produceFile = { File(filesDir, "datastore/$SETTINGS_FILE").absolutePath.toPath() },
            ),
        )
        Graph.app.syncAlerts(replace = false)
    }
}

/** Copies the bundled USDA database out of the APK on first use and after each app update. */
@Synchronized
private fun usdaFile(context: Context): String {
    val file = context.getDatabasePath(UsdaDatabase.DB_NAME)
    val prefs = context.getSharedPreferences("usda", Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    if (!file.exists() || prefs.getLong("stamp", -1L) != stamp) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${UsdaDatabase.DB_NAME}.tmp")
        context.assets.open(UsdaDatabase.DB_NAME).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        prefs.edit().putLong("stamp", stamp).apply()
    }
    return file.path
}
