package com.moblized.nutritrack.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.CustomFood
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.NutrientDef
import com.moblized.nutritrack.data.Nutrients
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(
    foodId: Long,
    barcode: String?,
    onSaved: (Long) -> Unit,
    onDone: () -> Unit,
) {
    var existing by remember { mutableStateOf<CustomFood?>(null) }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var servingLabel by remember { mutableStateOf("1 serving") }
    var servingGrams by remember { mutableStateOf("100") }
    val values = remember { mutableStateMapOf<Int, String>() }
    var showAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(foodId) {
        if (foodId <= 0) return@LaunchedEffect
        val f = Graph.app.db.customFoods().get(foodId) ?: return@LaunchedEffect
        existing = f
        name = f.name
        brand = f.brand
        servingLabel = f.servingLabel
        servingGrams = fmtQty(f.servingGrams)
        val per100 = NutrientCodec.decode(f.per100)
        per100.forEach { (id, v) -> values[id] = fmtAmount(v * f.servingGrams / 100.0).replace(",", "") }
        if (per100.keys.any { it !in Nutrients.labelFields }) showAll = true
    }

    val grams = parseNumber(servingGrams)
    val canSave = name.isNotBlank() && grams != null && grams > 0

    fun save() {
        val g = grams ?: return
        val per100 = HashMap<Int, Double>()
        values.forEach { (id, text) -> parseNumber(text)?.let { per100[id] = it / g * 100.0 } }
        val food = CustomFood(
            id = existing?.id ?: 0,
            name = name.trim(),
            brand = brand.trim(),
            barcode = existing?.barcode ?: barcode,
            servingLabel = servingLabel.trim().ifBlank { "1 serving" },
            servingGrams = g,
            per100 = NutrientCodec.encode(per100),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        scope.launch {
            val id = Graph.app.saveCustomFood(food)
            onSaved(id)
        }
    }

    val fields: List<NutrientDef> = remember(showAll) {
        val label = Nutrients.labelFields.mapNotNull { Nutrients.byId[it] }
        if (showAll) label + Nutrients.all.filter { it.id !in Nutrients.labelFields } else label
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (foodId > 0) "Edit food" else "New food") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (foodId > 0) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete food") }
                    }
                    TextButton(onClick = { save() }, enabled = canSave) { Text("Save") }
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
            if (barcode != null && foodId <= 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Barcode $barcode isn't in Open Food Facts yet. Copy the nutrition label below " +
                                "and next time this barcode will open your entry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        servingLabel, { servingLabel = it },
                        label = { Text("Serving") },
                        placeholder = { Text("1 bar") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        servingGrams, { servingGrams = it },
                        label = { Text("Grams") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Nutrition per serving", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Leave anything you don't know blank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(fields, key = { it.id }) { def ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = values[def.id] ?: "",
                        onValueChange = { values[def.id] = it },
                        singleLine = true,
                        suffix = { Text(def.unit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp),
                    )
                }
            }
            item {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show label nutrients only" else "Add vitamins, minerals & more")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $name?") },
            text = { Text("Diary entries already logged keep their nutrition.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Graph.app.db.customFoods().delete(foodId)
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
