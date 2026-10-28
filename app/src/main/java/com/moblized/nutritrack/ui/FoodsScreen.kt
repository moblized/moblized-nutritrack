package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.ui.theme.foodTileColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsScreen(onOpenRecipe: (Long) -> Unit, onOpenCustom: (Long) -> Unit, onImportMealie: (() -> Unit)?) {
    val recipes by remember { Graph.app.db.recipes().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val foods by remember { Graph.app.db.customFoods().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("My foods") }) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp),
        ) {
            item { SectionTitle("🍲 Recipes", "New recipe") { onOpenRecipe(0) } }
            if (onImportMealie != null) {
                item {
                    androidx.compose.material3.TextButton(
                        onClick = onImportMealie,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("🍲 Import from Mealie") }
                }
            }
            if (recipes.isEmpty()) {
                item { EmptyHint("👩‍🍳 Combine ingredients once, then log a serving in one tap.") }
            }
            items(recipes, key = { "r${it.id}" }) { r ->
                ListItem(
                    modifier = Modifier.clickable { onOpenRecipe(r.id) },
                    leadingContent = { EmojiTile(Emoji.food(r.name, source = FoodSource.RECIPE), foodTileColor(r.name)) },
                    headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${fmtQty(r.servings)} servings" + if (r.mealieSlug != null) " · from Mealie" else "")
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item { SectionTitle("🏷️ Foods & scans", "New food") { onOpenCustom(0) } }
            if (foods.isEmpty()) {
                item { EmptyHint("📦 Foods you scan or enter from a nutrition label show up here.") }
            }
            items(foods, key = { "c${it.id}" }) { f ->
                val kcal = NutrientCodec.decode(f.per100)[Nutrients.ENERGY]?.let { it * f.servingGrams / 100 }
                ListItem(
                    modifier = Modifier.clickable { onOpenCustom(f.id) },
                    leadingContent = {
                        EmojiTile(
                            if (f.barcode != null && Emoji.food(f.name) == "🍽️") "📦" else Emoji.food(f.name, source = FoodSource.CUSTOM),
                            foodTileColor(f.name),
                        )
                    },
                    headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                f.brand.ifBlank { null },
                                "${f.servingLabel} (${fmtAmount(f.servingGrams)} g)",
                                kcal?.let { "${fmtKcal(it)} kcal" },
                            ).joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(action)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
