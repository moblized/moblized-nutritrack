package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.DiaryEntry
import com.moblized.nutritrack.data.Gap
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.SuggestCandidate
import com.moblized.nutritrack.data.Suggestion
import com.moblized.nutritrack.data.Suggestions
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.sumNutrients
import com.moblized.nutritrack.ui.theme.foodTileColor
import com.moblized.nutritrack.ui.theme.warningColors
import java.time.LocalDate

@Composable
fun WhatToEatScreen(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    settings: Settings,
    initialFocus: Int?,
    onAdd: (SuggestCandidate) -> Unit,
) {
    val entries by remember(date) { Graph.app.db.diary().observeDay(date.toString()) }
        .collectAsStateWithLifecycle(initialValue = null)
    var pool by remember { mutableStateOf<List<SuggestCandidate>?>(null) }
    var mine by remember(date) { mutableStateOf<List<SuggestCandidate>>(emptyList()) }
    LaunchedEffect(Unit) { pool = Graph.app.suggestionPool() }
    LaunchedEffect(date, entries) { mine = Graph.app.personalCandidates(date) }
    var focus by rememberSaveable(initialFocus) { mutableStateOf(initialFocus) }

    WhatToEatContent(
        date = date,
        onDateChange = onDateChange,
        settings = settings,
        entries = entries,
        pool = pool,
        mine = mine,
        focus = focus,
        onFocus = { focus = it },
        onAdd = onAdd,
    )
}

/** The screen's look, free of data loading so it can be screenshot-tested. */
@Composable
fun WhatToEatContent(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    settings: Settings,
    entries: List<DiaryEntry>?,
    pool: List<SuggestCandidate>?,
    mine: List<SuggestCandidate>,
    focus: Int?,
    onFocus: (Int?) -> Unit,
    onAdd: (SuggestCandidate) -> Unit,
) {
    val targets = remember(settings) { Targets.compute(settings) }
    val totals = remember(entries) { sumNutrients(entries.orEmpty().map { NutrientCodec.decode(it.nutrients) }) }
    val gaps = remember(totals, targets) { Suggestions.gaps(totals, targets) }
    val remaining = Suggestions.remainingKcal(totals, targets)
    val activeFocus = focus?.takeIf { f -> gaps.any { it.id == f } }
    val ideas = remember(pool, totals, targets, activeFocus) {
        pool?.let { Suggestions.rank(it, totals, targets, activeFocus, limit = 15) }
    }
    val yours = remember(mine, totals, targets, activeFocus) {
        Suggestions.rank(mine, totals, targets, activeFocus, limit = 5)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { DateTopBar(date, onDateChange, title = "What to eat") },
    ) { pad ->
        if (entries == null || ideas == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { HeaderCard(entries.isEmpty(), remaining, gaps) }
            if (gaps.isNotEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = activeFocus == null, onClick = { onFocus(null) }, label = { Text("✨ Best overall") })
                        gaps.take(8).forEach { g ->
                            val name = Nutrients.byId[g.id]?.name.orEmpty()
                            FilterChip(
                                selected = activeFocus == g.id,
                                onClick = { onFocus(g.id) },
                                label = { Text("${Emoji.nutrient(g.id)} $name ${g.percentDone}%") },
                            )
                        }
                    }
                }
            }
            if (gaps.isEmpty()) {
                item {
                    Text(
                        "You've covered every key nutrient today. If you're still hungry, anything you enjoy works!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            } else {
                if (yours.isNotEmpty()) {
                    item { SectionHeader("⭐ From your foods", "Things you eat, in the amount you usually have") }
                    items(yours, key = { "mine-${it.candidate.source.key}-${it.candidate.id}" }) { s -> SuggestionCard(s, onAdd) }
                }
                item {
                    SectionHeader(
                        "💡 Ideas",
                        if (remaining <= Suggestions.LIGHT_KCAL) "Light picks, since you're close to your calorie target"
                        else "Everyday foods that fit the ${fmtKcal(remaining)} kcal you have left",
                    )
                }
                if (ideas.isEmpty()) {
                    item {
                        Text(
                            "Nothing fits that filter right now. Try \"Best overall\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(ideas, key = { "idea-${it.candidate.id}" }) { s -> SuggestionCard(s, onAdd) }
                item {
                    Text(
                        "Ranked by how much of what you still need each portion covers, for its calories. " +
                            "Portions are typical servings; you can change the amount when you add it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(nothingLogged: Boolean, remaining: Double, gaps: List<Gap>) {
    val low = gaps.filter { it.fraction > 0.5 }
    val over = remaining < 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (over) CardDefaults.cardColors(containerColor = warningColors().container, contentColor = warningColors().content)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile("🍽️", MaterialTheme.colorScheme.primaryContainer, size = 54.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    when {
                        over -> "${fmtKcal(-remaining)} kcal over today"
                        else -> "${fmtKcal(remaining)} kcal left today"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        nothingLogged -> "Nothing logged yet. Here are nutrient-packed ways to start."
                        low.isEmpty() && gaps.isNotEmpty() -> "Almost there! A few small gaps left."
                        gaps.isEmpty() -> "Every key nutrient covered."
                        else -> "Most needed: " + low.take(3).joinToString(", ") { "${Emoji.nutrient(it.id)} ${Nutrients.byId[it.id]?.name}" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, onAdd: (SuggestCandidate) -> Unit) {
    val c = s.candidate
    val name = Suggestions.displayName(c.name)
    Card(Modifier.fillMaxWidth().clickable { onAdd(c) }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(Emoji.food(name, c.category, c.source), foodTileColor(name), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${portionText(c)} · ${fmtKcal(c.kcal)} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    s.gains.take(3).joinToString("   ") { (id, p) -> "${Emoji.nutrient(id)} +$p% ${shortName(id)}" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { onAdd(c) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add $name")
            }
        }
    }
}

private fun portionText(c: SuggestCandidate): String = when (c.unitLabel) {
    "g" -> "${fmtAmount(c.grams)} g"
    "oz" -> "${fmtQty(c.quantity)} oz"
    else -> if (c.quantity == 1.0) c.unitLabel else "${fmtQty(c.quantity)} × ${c.unitLabel}"
}

private fun shortName(id: Int): String = when (id) {
    Nutrients.VIT_A -> "vit A"
    Nutrients.VIT_C -> "vit C"
    Nutrients.VIT_D -> "vit D"
    Nutrients.VIT_E -> "vit E"
    Nutrients.VIT_K -> "vit K"
    Nutrients.VIT_B12 -> "B12"
    Nutrients.FOLATE -> "folate"
    else -> Nutrients.byId[id]?.name?.lowercase().orEmpty()
}

/** Which meal to add a suggestion to: the meal for this time of day, or snacks for other days. */
fun suggestedMeal(date: LocalDate): Int =
    if (date == LocalDate.now()) com.moblized.nutritrack.data.Meals.forHour(java.time.LocalTime.now().hour) else 3
