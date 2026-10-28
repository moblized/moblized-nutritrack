package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.LowNutrients
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.data.sumNutrients
import java.time.LocalDate
import java.time.LocalDateTime
import com.moblized.nutritrack.ui.theme.warningColors

private enum class Range(val label: String, val days: Long) { DAY("Day", 1), WEEK("7-day avg", 7), MONTH("30-day avg", 30) }

@Composable
fun NutrientsScreen(date: LocalDate, onDateChange: (LocalDate) -> Unit, settings: Settings) {
    var rangeName by rememberSaveable { mutableStateOf(Range.DAY.name) }
    val range = Range.valueOf(rangeName)
    val from = date.minusDays(range.days - 1)
    val entries by remember(date, range) {
        Graph.app.db.diary().observeRange(from.toString(), date.toString())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val loggedDays = remember(entries) { entries.map { it.date }.distinct().size }
    val totals = remember(entries, loggedDays) {
        val sum = sumNutrients(entries.map { NutrientCodec.decode(it.nutrients) })
        if (loggedDays > 1) sum.scaled(1.0 / loggedDays) else sum
    }
    val targets = remember(settings) { Targets.compute(settings) }
    val uls = remember(settings) { Targets.upperLimits(settings) }

    // Low markers: a day once it's mostly over; averages over at least 3 logged days.
    val lowIds = remember(totals, targets, range, date, loggedDays, settings.lowWarnings) {
        when {
            !settings.lowWarnings || entries.isEmpty() -> emptySet()
            range == Range.DAY && LowNutrients.dayIsJudged(date, LocalDateTime.now()) ->
                LowNutrients.below(totals, targets, LowNutrients.DAY_THRESHOLD).map { it.id }.toSet()
            range != Range.DAY && loggedDays >= LowNutrients.MIN_WEEK_DAYS ->
                LowNutrients.below(totals, targets, LowNutrients.WEEK_THRESHOLD).map { it.id }.toSet()
            else -> emptySet()
        }
    }

    val goalDefs = Nutrients.all.filter { it.kind == TargetKind.GOAL && targets[it.id] != null }
    val met = goalDefs.count { (totals[it.id] ?: 0.0) >= (targets[it.id] ?: Double.MAX_VALUE) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { DateTopBar(date, onDateChange, title = "Nutrients") },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 24.dp),
        ) {
            item {
                ChoiceRow(
                    options = Range.entries.toList(),
                    selected = range,
                    label = { it.label },
                    onSelect = { rangeName = it.name },
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                TargetsMetCard(
                    met = met,
                    total = goalDefs.size,
                    subtitle = when {
                        entries.isEmpty() -> "Nothing logged for this period yet."
                        range == Range.DAY -> "${fmtKcal(totals[Nutrients.ENERGY] ?: 0.0)} kcal from ${entries.size} foods"
                        else -> "Averaged over $loggedDays logged ${if (loggedDays == 1) "day" else "days"}"
                    },
                    lowCount = lowIds.size,
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                NutrientBreakdown(
                    totals = totals, targets = targets, upperLimits = uls, hideEmptyInfo = false,
                    lowIds = lowIds, lowLabel = if (range == Range.DAY) "Running low" else "Low on average",
                )
            }
            item {
                Text(
                    "Targets follow the US Dietary Reference Intakes for your age and sex. " +
                        "A food missing a nutrient in USDA data counts as 0, so some totals may be underestimated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** How many nutrient goals are met, shown as a little plant that grows into a trophy. */
@Composable
fun TargetsMetCard(met: Int, total: Int, subtitle: String, lowCount: Int = 0) {
    val frac = if (total > 0) met.toFloat() / total else 0f
    val badge = when {
        frac >= 0.75f -> "🏆"
        frac >= 0.5f -> "🌳"
        frac >= 0.25f -> "🌿"
        else -> "🌱"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(badge, MaterialTheme.colorScheme.primaryContainer, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("$met of $total goals hit", style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lowCount > 0) {
                    Text(
                        "⚠️ $lowCount running low",
                        style = MaterialTheme.typography.labelLarge,
                        color = warningColors().accent,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(frac, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
            }
        }
    }
}
