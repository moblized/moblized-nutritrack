package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.DiaryEntry
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Meals
import com.moblized.nutritrack.data.MealieException
import com.moblized.nutritrack.data.MealiePlanEntry
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.sumNutrients
import com.moblized.nutritrack.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    settings: Settings,
    onAdd: (Int) -> Unit,
    onOpenEntry: (DiaryEntry) -> Unit,
    onShowNutrients: () -> Unit,
    onSetupProfile: () -> Unit,
    onOpenMealPlan: (MealiePlanEntry) -> Unit,
) {
    val entries by remember(date) { Graph.app.db.diary().observeDay(date.toString()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val totals = remember(entries) { sumNutrients(entries.map { NutrientCodec.decode(it.nutrients) }) }
    val targets = remember(settings) { Targets.compute(settings) }

    // Today's Mealie meal plan, when a Mealie server is connected.
    var plan by remember(date) { mutableStateOf<List<MealiePlanEntry>>(emptyList()) }
    var planError by remember(date) { mutableStateOf<String?>(null) }
    LaunchedEffect(date, settings.mealieUrl, settings.mealieToken) {
        plan = emptyList()
        planError = null
        val client = Graph.app.mealie() ?: return@LaunchedEffect
        try {
            plan = client.mealPlan(date).sortedBy { it.meal }
        } catch (e: MealieException) {
            planError = e.message
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DateTopBar(date = date, onDateChange = onDateChange)
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!settings.profileSet) {
                item {
                    Card(
                        onClick = onSetupProfile,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Personalize your targets", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add your age, sex, height and weight so calorie and nutrient targets fit you.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item { SummaryCard(totals, targets) }
            if (plan.isNotEmpty() || planError != null) {
                item { MealPlanCard(plan, planError, onOpenMealPlan) }
            }
            item { HighlightsCard(totals, targets, onShowNutrients) }
            for (meal in Meals.names.indices) {
                item(key = "meal-$meal") {
                    MealCard(
                        meal = meal,
                        entries = entries.filter { it.meal == meal },
                        onAdd = { onAdd(meal) },
                        onOpen = onOpenEntry,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTopBar(date: LocalDate, onDateChange: (LocalDate) -> Unit, title: String? = null) {
    var picking by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateLabel(date))
            }
        },
        actions = {
            IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
            }
            IconButton(onClick = { picking = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick a date")
            }
            IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
        },
    )
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDateChange(LocalDate.now())
                    picking = false
                }) { Text("Today") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun SummaryCard(totals: Map<Int, Double>, targets: Map<Int, Double>) {
    val eaten = totals[Nutrients.ENERGY] ?: 0.0
    val target = targets[Nutrients.ENERGY] ?: 2000.0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalorieRing(eaten = eaten, target = target)
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Eaten", "${fmtKcal(eaten)} kcal")
                    Stat("Target", "${fmtKcal(target)} kcal")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroColumn("Protein", totals[Nutrients.PROTEIN] ?: 0.0, targets[Nutrients.PROTEIN], NutrientGroup.PROTEIN.color(), Modifier.weight(1f))
                MacroColumn("Carbs", totals[Nutrients.CARBS] ?: 0.0, targets[Nutrients.CARBS], NutrientGroup.CARBS.color(), Modifier.weight(1f))
                MacroColumn("Fat", totals[Nutrients.FAT] ?: 0.0, targets[Nutrients.FAT], NutrientGroup.LIPIDS.color(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MealPlanCard(plan: List<MealiePlanEntry>, error: String?, onOpen: (MealiePlanEntry) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Mealie meal plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            plan.forEach { e ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = e.recipeSlug != null) { onOpen(e) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            e.entryType.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e.recipeSlug != null) {
                        Text("Log", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HighlightsCard(totals: Map<Int, Double>, targets: Map<Int, Double>, onShowAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Key nutrients", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onShowAll) { Text("All ${Nutrients.all.size}") }
            }
            Nutrients.highlights.mapNotNull { Nutrients.byId[it] }.forEach { def ->
                NutrientRow(def, totals[def.id] ?: 0.0, targets[def.id], null)
            }
        }
    }
}

@Composable
private fun MealCard(meal: Int, entries: List<DiaryEntry>, onAdd: () -> Unit, onOpen: (DiaryEntry) -> Unit) {
    val kcal = entries.sumOf { NutrientCodec.decode(it.nutrients)[Nutrients.ENERGY] ?: 0.0 }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Meals.name(meal), style = MaterialTheme.typography.titleMedium)
                    if (entries.isNotEmpty()) {
                        Text(
                            "${fmtKcal(kcal)} kcal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "Add to ${Meals.name(meal)}", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (entries.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            entries.forEach { e ->
                val eKcal = NutrientCodec.decode(e.nutrients)[Nutrients.ENERGY] ?: 0.0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(e) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${fmtQty(e.quantity)} ${e.unitLabel} · ${fmtAmount(e.grams)} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(fmtKcal(eKcal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
