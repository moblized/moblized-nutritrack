package com.moblized.nutritrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.moblized.nutritrack.MainActivity
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.LowNutrients
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.sumNutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

private data class WidgetData(
    val kcal: Double,
    val kcalTarget: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val low: List<Pair<String, Int>>,
    /** True once the day is mostly over and something is under half its target. */
    val warn: Boolean,
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { load() }
        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }

    private suspend fun load(): WidgetData {
        val app = Graph.app
        val settings = app.settings.current()
        val targets = Targets.compute(settings)
        val entries = app.db.diary().day(LocalDate.now().toString())
        val totals = sumNutrients(entries.map { NutrientCodec.decode(it.nutrients) })
        // Lowest key micronutrients as % of target, to show what to eat more of.
        val candidates = listOf(
            Nutrients.FIBER to "Fiber", Nutrients.VIT_D to "Vit D", Nutrients.VIT_C to "Vit C",
            Nutrients.POTASSIUM to "Potassium", Nutrients.MAGNESIUM to "Magnesium", Nutrients.CALCIUM to "Calcium",
            Nutrients.IRON to "Iron", Nutrients.VIT_B12 to "B12", Nutrients.FOLATE to "Folate", Nutrients.ZINC to "Zinc",
        )
        val low = candidates.filter { settings.detailedNutrition || it.first in Nutrients.essentials }.mapNotNull { (id, label) ->
            val t = targets[id] ?: return@mapNotNull null
            if (Nutrients.byId[id]?.kind != TargetKind.GOAL || t <= 0) return@mapNotNull null
            label to ((totals[id] ?: 0.0) / t * 100).roundToInt()
        }.sortedBy { it.second }.take(3)
        return WidgetData(
            kcal = totals[Nutrients.ENERGY] ?: 0.0,
            kcalTarget = targets[Nutrients.ENERGY] ?: 2000.0,
            protein = totals[Nutrients.PROTEIN] ?: 0.0,
            carbs = totals[Nutrients.CARBS] ?: 0.0,
            fat = totals[Nutrients.FAT] ?: 0.0,
            low = low,
            warn = LowNutrients.dayIsJudged(LocalDate.now(), java.time.LocalDateTime.now()) &&
                low.any { it.second < (LowNutrients.DAY_THRESHOLD * 100).toInt() },
        )
    }
}

@Composable
private fun Content(d: WidgetData) {
    val fmt = { v: Double -> String.format(Locale.US, "%,d", v.roundToInt()) }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "🥗 Today",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Text(
            "${fmt(d.kcal)} / ${fmt(d.kcalTarget)} kcal",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (d.kcalTarget > 0) (d.kcal / d.kcalTarget).toFloat().coerceIn(0f, 1f) else 0f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Macro("💪 Protein", d.protein, GlanceModifier.defaultWeight())
            Macro("🍞 Carbs", d.carbs, GlanceModifier.defaultWeight())
            Macro("🥑 Fat", d.fat, GlanceModifier.defaultWeight())
        }
        if (d.low.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                (if (d.warn) "⚠️ Low: " else "Lowest so far: ") + d.low.joinToString("  ·  ") { "${it.first} ${it.second}%" },
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Text(
            "${grams.roundToInt()} g",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
