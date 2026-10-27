package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.IngredientMatcher
import com.moblized.nutritrack.data.MealieException
import com.moblized.nutritrack.data.MealieIngredient
import com.moblized.nutritrack.data.MealieRecipe
import com.moblized.nutritrack.data.MealieRecipeSummary
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Recipe
import com.moblized.nutritrack.data.RecipeItem
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.data.sumNutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealieBrowseScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MealieRecipeSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val linked by remember { Graph.app.db.recipes().observeMealieLinked() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val linkedSlugs = remember(linked) { linked.mapNotNull { it.mealieSlug }.toSet() }

    LaunchedEffect(query) {
        delay(if (query.isBlank()) 0 else 300)
        loading = true
        error = null
        val client = Graph.app.mealie()
        if (client == null) {
            error = "Add your Mealie server in Settings first."
        } else {
            try {
                results = client.recipes(query)
            } catch (e: MealieException) {
                error = e.message
            }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Mealie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your Mealie recipes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (!loading && error == null && results.isEmpty()) {
                Text(
                    if (query.isBlank()) "No recipes on your Mealie server yet." else "No recipes match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results, key = { it.slug }) { r ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(r.slug) },
                        headlineContent = { Text(r.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            if (r.description.isNotBlank()) {
                                Text(r.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        trailingContent = {
                            if (r.slug in linkedSlugs) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Already imported",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private data class ImportRow(
    val ingredient: MealieIngredient,
    val item: RecipeItem?,
    val include: Boolean,
    val estimated: Boolean,
    val missingAmount: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealieImportScreen(slug: String, onBack: () -> Unit, onImported: (Long) -> Unit) {
    var recipe by remember { mutableStateOf<MealieRecipe?>(null) }
    var existing by remember { mutableStateOf<Recipe?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var servingsText by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<ImportRow>() }
    var matched by remember { mutableIntStateOf(0) }
    var matching by remember { mutableStateOf(true) }
    var pushNutrition by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var pickIndex by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slug) {
        val client = Graph.app.mealie()
        if (client == null) {
            error = "Add your Mealie server in Settings first."
            matching = false
            return@LaunchedEffect
        }
        val r = try {
            client.recipe(slug)
        } catch (e: MealieException) {
            error = e.message
            matching = false
            return@LaunchedEffect
        }
        recipe = r
        existing = Graph.app.db.recipes().byMealieSlug(slug)
        servingsText = fmtQty(r.servings ?: existing?.servings ?: 1.0)
        val matcher = IngredientMatcher(Graph.app.usda)
        rows.clear()
        for (ing in r.ingredients) {
            val m = withContext(Dispatchers.IO) { runCatching { matcher.match(ing) }.getOrNull() }
            rows += ImportRow(
                ingredient = ing,
                item = m?.item,
                include = m != null && m.item != null && !m.missingAmount,
                estimated = m?.estimated ?: false,
                missingAmount = m?.missingAmount ?: false,
            )
            matched++
        }
        matching = false
    }

    val servings = parseNumber(servingsText)?.takeIf { it > 0 } ?: 1.0
    val included = rows.filter { it.include && it.item != null }.mapNotNull { it.item }
    val total = sumNutrients(included.map { NutrientCodec.decode(it.per100).scaled(it.grams / 100.0) })
    val perServing = total.scaled(1.0 / servings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "Mealie recipe", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val r = recipe
            if (r != null && !matching) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        enabled = included.isNotEmpty() && !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val saved = Recipe(
                                    id = existing?.id ?: 0,
                                    name = r.name,
                                    servings = servings,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                    mealieSlug = r.slug,
                                )
                                val id = Graph.app.saveRecipe(saved, included)
                                if (pushNutrition) {
                                    try {
                                        Graph.app.mealie()?.saveNutrition(r.slug, perServing)
                                    } catch (e: MealieException) {
                                        snackbar.showSnackbar(
                                            "Saved here, but Mealie didn't take the nutrition: ${e.message}",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                                saving = false
                                onImported(id)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        Text(if (existing != null) "Update recipe" else "Import ${included.size} ingredients")
                    }
                }
            }
        },
    ) { pad ->
        val r = recipe
        when {
            error != null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
            r == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (matching) {
                        Text(
                            "Matching ingredients to USDA foods… $matched of ${r.ingredients.size}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { if (r.ingredients.isEmpty()) 1f else matched.toFloat() / r.ingredients.size },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    } else {
                        Text(
                            "Check each match. Tap an ingredient to change the food or amount; " +
                                "untick anything you want to leave out.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (existing != null) {
                        Text(
                            "Already imported. Saving replaces the NutriTrack copy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            servingsText, { servingsText = it },
                            label = { Text("Servings") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp),
                        )
                        Column {
                            Text("${fmtKcal(perServing[Nutrients.ENERGY] ?: 0.0)} kcal per serving", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "P ${fmtAmount(perServing[Nutrients.PROTEIN] ?: 0.0)} g · " +
                                    "C ${fmtAmount(perServing[Nutrients.CARBS] ?: 0.0)} g · " +
                                    "F ${fmtAmount(perServing[Nutrients.FAT] ?: 0.0)} g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(rows, key = { i, _ -> i }) { index, row ->
                    ImportRowView(
                        row = row,
                        onToggle = { on -> rows[index] = row.copy(include = on && row.item != null) },
                        onEditAmount = { if (row.item != null) editIndex = index else pickIndex = index },
                        onChangeFood = { pickIndex = index },
                    )
                }
                if (!matching) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Save nutrition to Mealie too", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Fills in the recipe's calories, macros, sodium and cholesterol per serving in Mealie",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = pushNutrition, onCheckedChange = { pushNutrition = it })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    editIndex?.let { i ->
        val row = rows.getOrNull(i)
        val item = row?.item
        if (row == null || item == null) {
            editIndex = null
        } else {
            IngredientAmountDialog(
                source = FoodSource.of(item.source),
                id = item.refId,
                initial = item,
                onDismiss = { editIndex = null },
                onConfirm = { updated ->
                    rows[i] = row.copy(item = updated, include = true, estimated = false, missingAmount = false)
                    editIndex = null
                },
            )
        }
    }

    pickIndex?.let { i ->
        val row = rows.getOrNull(i)
        if (row == null) {
            pickIndex = null
        } else {
            IngredientPicker(
                excludeRecipeId = existing?.id ?: 0L,
                onDismiss = { pickIndex = null },
                onPicked = { item ->
                    rows[i] = row.copy(item = item, include = true, estimated = false, missingAmount = false)
                    pickIndex = null
                },
            )
        }
    }
}

@Composable
private fun ImportRowView(
    row: ImportRow,
    onToggle: (Boolean) -> Unit,
    onEditAmount: () -> Unit,
    onChangeFood: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.clickable(onClick = onEditAmount).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Checkbox(checked = row.include, onCheckedChange = onToggle, enabled = row.item != null)
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(row.ingredient.text, style = MaterialTheme.typography.bodyMedium)
                val item = row.item
                if (item == null) {
                    Text(
                        "No match found. Tap to choose a food.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val kcal = (NutrientCodec.decode(item.per100)[Nutrients.ENERGY] ?: 0.0) * item.grams / 100
                    Text(
                        "${fmtQty(item.quantity)} × ${item.unitLabel} = ${fmtAmount(item.grams)} g · ${fmtKcal(kcal)} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        row.missingAmount -> Text(
                            "No amount in the recipe. Tap to set one, or leave it out.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        row.estimated -> Text(
                            "Amount in grams is estimated. Tap to check.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    TextButton(onClick = onChangeFood, contentPadding = PaddingValues(0.dp)) {
                        Text("Change food")
                    }
                }
            }
        }
    }
}
