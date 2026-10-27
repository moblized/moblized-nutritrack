package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moblized.nutritrack.data.Food
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.FoodSummary
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Portion
import com.moblized.nutritrack.data.Recipe
import com.moblized.nutritrack.data.RecipeItem
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.data.sumNutrients
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    recipeId: Long,
    onSaved: (Long) -> Unit,
    onDone: () -> Unit,
) {
    var existing by remember { mutableStateOf<Recipe?>(null) }
    var name by remember { mutableStateOf("") }
    var servingsText by remember { mutableStateOf("4") }
    val items = remember { mutableStateListOf<RecipeItem>() }
    var picking by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recipeId) {
        if (recipeId <= 0) return@LaunchedEffect
        val r = Graph.app.db.recipes().get(recipeId) ?: return@LaunchedEffect
        existing = r
        name = r.name
        servingsText = fmtQty(r.servings)
        items.clear()
        items.addAll(Graph.app.db.recipes().items(recipeId))
    }

    val servings = parseNumber(servingsText)?.takeIf { it > 0 } ?: 1.0
    val totalGrams = items.sumOf { it.grams }
    val total = sumNutrients(items.map { NutrientCodec.decode(it.per100).scaled(it.grams / 100.0) })
    val perServing = total.scaled(1.0 / servings)
    val canSave = name.isNotBlank() && items.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId > 0) "Edit recipe" else "New recipe") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (recipeId > 0) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete recipe") }
                    }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            scope.launch {
                                val r = Recipe(
                                    id = existing?.id ?: 0,
                                    name = name.trim(),
                                    servings = servings,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                )
                                val id = Graph.app.saveRecipe(r, items.toList())
                                onSaved(id)
                            }
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Recipe name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(
                    servingsText, { servingsText = it },
                    label = { Text("Servings it makes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(180.dp),
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryStat("Per serving", "${fmtKcal(perServing[Nutrients.ENERGY] ?: 0.0)} kcal")
                        SummaryStat("Protein", "${fmtAmount(perServing[Nutrients.PROTEIN] ?: 0.0)} g")
                        SummaryStat("Carbs", "${fmtAmount(perServing[Nutrients.CARBS] ?: 0.0)} g")
                        SummaryStat("Fat", "${fmtAmount(perServing[Nutrients.FAT] ?: 0.0)} g")
                    }
                }
                Text(
                    "Serving ≈ ${fmtAmount(totalGrams / servings)} g · whole recipe ${fmtAmount(totalGrams)} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Ingredients", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(items, key = { i, ri -> "$i-${ri.source}-${ri.refId}" }) { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingIndex = index }
                        .padding(vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${fmtQty(item.quantity)} ${item.unitLabel} · ${fmtAmount(item.grams)} g · " +
                                "${fmtKcal((NutrientCodec.decode(item.per100)[Nutrients.ENERGY] ?: 0.0) * item.grams / 100)} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { items.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ingredient")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add ingredient")
                }
            }
        }
    }

    if (picking) {
        IngredientPicker(
            excludeRecipeId = recipeId,
            onDismiss = { picking = false },
            onPicked = { item ->
                items.add(item)
                picking = false
            },
        )
    }

    editingIndex?.let { index ->
        val current = items.getOrNull(index)
        if (current == null) {
            editingIndex = null
        } else {
            IngredientAmountDialog(
                source = FoodSource.of(current.source),
                id = current.refId,
                initial = current,
                onDismiss = { editingIndex = null },
                onConfirm = { updated ->
                    items[index] = updated
                    editingIndex = null
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recipe?") },
            text = { Text("Diary entries already logged keep their nutrition.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Graph.app.deleteRecipe(recipeId)
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPicker(excludeRecipeId: Long, onDismiss: () -> Unit, onPicked: (RecipeItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<FoodSummary?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(180)
        results = Graph.app.search(query).filterNot { it.source == FoodSource.RECIPE && it.id == excludeRecipeId }
        searching = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Add ingredient") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                        },
                    )
                },
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search foods") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { "${it.source.key}-${it.id}" }) { f ->
                            FoodResultRow(f) { chosen = f }
                        }
                    }
                }
            }
        }
    }

    chosen?.let { f ->
        IngredientAmountDialog(
            source = f.source,
            id = f.id,
            initial = null,
            onDismiss = { chosen = null },
            onConfirm = {
                chosen = null
                onPicked(it)
            },
        )
    }
}

@Composable
private fun IngredientAmountDialog(
    source: FoodSource,
    id: Long,
    initial: RecipeItem?,
    onDismiss: () -> Unit,
    onConfirm: (RecipeItem) -> Unit,
) {
    var food by remember { mutableStateOf<Food?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(Portion("g", 1.0)) }

    LaunchedEffect(source, id) {
        val f = Graph.app.loadFood(source, id)
        if (f == null && initial == null) {
            onDismiss()
            return@LaunchedEffect
        }
        if (initial != null) {
            qtyText = fmtQty(initial.quantity)
            unit = Portion(initial.unitLabel, initial.unitGrams)
        } else if (f != null) {
            qtyText = fmtQty(f.defaultQuantity())
            unit = f.defaultUnit()
        }
        food = f
    }

    val qty = parseNumber(qtyText)
    val f = food
    // If the original food was deleted, the item can still be resized using its saved snapshot.
    val per100 = f?.per100g ?: initial?.let { NutrientCodec.decode(it.per100) }
    val units = f?.units ?: listOf(unit, Portion("g", 1.0), Portion("oz", Food.OZ)).distinct()
    val name = f?.name ?: initial?.name ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(100.dp),
                    )
                    UnitPicker(units, unit, { unit = it }, Modifier.weight(1f).padding(top = 8.dp))
                }
                val grams = (qty ?: 0.0) * unit.grams
                Text(
                    "${fmtAmount(grams)} g · ${fmtKcal((per100?.get(Nutrients.ENERGY) ?: 0.0) * grams / 100)} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = qty != null && qty > 0 && per100 != null,
                onClick = {
                    val q = qty ?: 0.0
                    onConfirm(
                        RecipeItem(
                            recipeId = initial?.recipeId ?: 0,
                            source = (f?.source ?: source).key,
                            refId = f?.id ?: id,
                            name = name,
                            quantity = q,
                            unitLabel = unit.label,
                            unitGrams = unit.grams,
                            grams = q * unit.grams,
                            per100 = NutrientCodec.encode(per100 ?: emptyMap()),
                        ),
                    )
                },
            ) { Text(if (initial == null) "Add" else "Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
