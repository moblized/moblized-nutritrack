package com.moblized.nutritrack.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moblized.nutritrack.MainActivity
import com.moblized.nutritrack.R
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.LowNutrients
import com.moblized.nutritrack.data.LowWarnings
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.minusDays
import com.moblized.nutritrack.data.now
import kotlinx.datetime.atTime
import com.moblized.nutritrack.ui.Emoji
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Schedules the evening "running low" check and posts its notification. */
object LowNutrientAlerts {
    private const val WORK_NAME = "low-nutrient-check"
    private const val CHANNEL_ID = "low_nutrients"
    private const val NOTIFICATION_ID = 4101

    /** Schedules the next check at [minutes] after midnight. [replace] = false keeps an existing schedule. */
    fun schedule(context: Context, minutes: Int, replace: Boolean = true) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong())
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val request = OneTimeWorkRequestBuilder<LowNutrientWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Low nutrient warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "An evening heads-up when a key nutrient is running low"
                },
            )
        }
    }

    /** Builds the notification text, or null when there's nothing worth warning about. */
    fun message(w: LowWarnings, includeWeek: Boolean): Pair<String, String>? {
        val week = if (includeWeek) w.week else emptyList()
        if (w.today.isEmpty() && week.isEmpty()) return null
        val first = (w.today.firstOrNull() ?: week.first())
        val title = if (w.today.size + week.size == 1) {
            "⚠️ Running low on ${first.name}"
        } else {
            "⚠️ A few nutrients are running low"
        }
        val lines = buildList {
            if (w.today.isNotEmpty()) add("Today: ${LowNutrients.summary(w.today)}")
            if (week.isNotEmpty()) add("Past ${w.weekDays} days on average: ${LowNutrients.summary(week)}")
            val tips = Emoji.boosters(first.id)
            if (tips.isNotEmpty()) add("Try ${tips.take(3).joinToString(", ") { "${it.first} ${it.second}" }}")
        }
        return title to lines.joinToString("\n")
    }

    fun post(context: Context, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFF2DB36F.toInt())
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and posting.
        }
    }
}

class LowNutrientWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = Graph.app
        val settings = app.settings.current()
        if (!settings.lowNotify) return Result.success()
        try {
            val today = kotlinx.datetime.LocalDate.now()
            val dayEntries = app.db.diary().day(today.toString())
            val recent = app.db.diary().range(today.minusDays(30).toString(), today.toString())
            // The person picked this time, so judge the day now even if it's before the usual evening cutoff.
            val w = LowNutrients.warnings(
                today, today.atTime(23, 59), dayEntries, recent, Targets.compute(settings), settings.detailedNutrition,
            )
            // The weekly average is only mentioned on Sundays so it doesn't repeat every night.
            val msg = LowNutrientAlerts.message(w, includeWeek = today.dayOfWeek == DayOfWeek.SUNDAY)
            if (msg != null) LowNutrientAlerts.post(applicationContext, msg.first, msg.second)
        } finally {
            LowNutrientAlerts.schedule(applicationContext, settings.lowNotifyMinutes)
        }
        return Result.success()
    }
}
