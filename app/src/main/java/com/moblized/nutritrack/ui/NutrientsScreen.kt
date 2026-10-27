package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.data.sumNutrients
import java.time.LocalDate

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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "$met of ${goalDefs.size} targets met",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                entries.isEmpty() -> "Nothing logged for this period yet."
                                range == Range.DAY -> "${fmtKcal(totals[Nutrients.ENERGY] ?: 0.0)} kcal from ${entries.size} foods"
                                else -> "Averaged over $loggedDays logged ${if (loggedDays == 1) "day" else "days"}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item {
                NutrientBreakdown(totals = totals, targets = targets, upperLimits = uls, hideEmptyInfo = false)
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
