package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.Food
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Meals
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Portion
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.ui.theme.BigNumber
import com.moblized.nutritrack.ui.theme.color
import com.moblized.nutritrack.ui.theme.foodTileColor
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    source: FoodSource,
    foodId: Long,
    date: LocalDate,
    initialMeal: Int,
    entryId: Long,
    settings: Settings,
    presetUnit: String? = null,
    presetUnitGrams: Double? = null,
    presetQty: Double? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onEditFood: (FoodSource, Long) -> Unit,
) {
    var food by remember { mutableStateOf<Food?>(null) }
    var missing by remember { mutableStateOf(false) }
    var qtyText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(Portion("g", 1.0)) }
    var meal by remember { mutableIntStateOf(initialMeal) }
    var entryDate by remember { mutableStateOf(date) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(source, foodId, entryId) {
        val f = Graph.app.loadFood(source, foodId)
        if (f == null) {
            missing = true
            return@LaunchedEffect
        }
        val existing = if (entryId > 0) Graph.app.db.diary().get(entryId) else null
        if (existing != null) {
            qtyText = fmtQty(existing.quantity)
            unit = f.units.firstOrNull { it.label == existing.unitLabel && kotlin.math.abs(it.grams - existing.unitGrams) < 0.01 }
                ?: Portion(existing.unitLabel, existing.unitGrams)
            meal = existing.meal
            entryDate = LocalDate.parse(existing.date)
        } else if (presetUnit != null) {
            // Coming from "What to eat": start with the suggested portion.
            unit = f.units.firstOrNull { it.label == presetUnit } ?: Portion(presetUnit, presetUnitGrams ?: 1.0)
            qtyText = fmtQty(presetQty ?: 1.0)
        } else {
            qtyText = fmtQty(f.defaultQuantity())
            unit = f.defaultUnit()
        }
        food = f
    }

    val targets = remember(settings) { Targets.compute(settings) }
    val uls = remember(settings) { Targets.upperLimits(settings) }
    val qty = parseNumber(qtyText)
    val grams = (qty ?: 0.0) * unit.grams
    val f = food
    val nutrients = remember(f, grams) { f?.per100g?.scaled(grams / 100.0) ?: emptyMap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId > 0) "Edit entry" else "Add food") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (f != null && f.source != FoodSource.USDA) {
                        IconButton(onClick = { onEditFood(f.source, f.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit food")
                        }
                    }
                    if (entryId > 0) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove from diary")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (f != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            qty?.let { q ->
                                scope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Graph.app.saveEntry(entryId, entryDate, meal, f, q, unit)
                                    onSaved()
                                }
                            }
                        },
                        enabled = qty != null && qty > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        Text(
                            if (entryId > 0) "Save changes" else "${Emoji.meal(meal)} Add to ${Meals.name(meal)} · ${fmtKcal(nutrients[Nutrients.ENERGY] ?: 0.0)} kcal",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
    ) { pad ->
        when {
            missing -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This food is no longer available.", modifier = Modifier.padding(24.dp))
            }
            f == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val category = if (f.source == FoodSource.USDA) f.detail.substringBefore(" · ") else null
                        EmojiTile(Emoji.food(f.name, category, f.source), foodTileColor(f.name), size = 60.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(f.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                f.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("Amount") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp),
                        )
                        UnitPicker(
                            units = f.units,
                            selected = unit,
                            onSelect = { unit = it },
                            modifier = Modifier.weight(1f).padding(top = 8.dp),
                        )
                    }
                    Text(
                        "= ${fmtAmount(grams)} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    ChoiceRow(
                        options = Meals.names.indices.toList(),
                        selected = meal,
                        label = { "${Emoji.meal(it)} ${Meals.name(it)}" },
                        onSelect = { meal = it },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1.1f)) {
                                Text(fmtKcal(nutrients[Nutrients.ENERGY] ?: 0.0), style = BigNumber)
                                Text(
                                    "kcal · ${pct(nutrients[Nutrients.ENERGY] ?: 0.0, targets[Nutrients.ENERGY]) ?: 0}% of daily",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MacroPill("💪 Protein", nutrients[Nutrients.PROTEIN] ?: 0.0, NutrientGroup.PROTEIN, Modifier.weight(1f))
                            MacroPill("🍞 Carbs", nutrients[Nutrients.CARBS] ?: 0.0, NutrientGroup.CARBS, Modifier.weight(1f))
                            MacroPill("🥑 Fat", nutrients[Nutrients.FAT] ?: 0.0, NutrientGroup.LIPIDS, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "% of your daily targets in this amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    NutrientBreakdown(totals = nutrients, targets = targets, upperLimits = uls)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove from diary?") },
            text = { Text(food?.name ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Graph.app.deleteEntry(entryId)
                        onSaved()
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MacroPill(label: String, grams: Double, group: NutrientGroup, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${fmtAmount(grams)} g", style = MaterialTheme.typography.titleMedium, color = group.color())
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
