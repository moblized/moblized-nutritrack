package com.moblized.nutritrack.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblized.nutritrack.data.DiaryEntry
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.LowNutrients
import com.moblized.nutritrack.data.LowWarnings
import com.moblized.nutritrack.data.Meals
import com.moblized.nutritrack.data.MealieException
import com.moblized.nutritrack.data.MealiePlanEntry
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Progress
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.sumNutrients
import com.moblized.nutritrack.ui.theme.color
import com.moblized.nutritrack.ui.theme.foodTileColor
import com.moblized.nutritrack.ui.theme.mealColor
import com.moblized.nutritrack.ui.theme.streakColors
import com.moblized.nutritrack.ui.theme.trackColor
import com.moblized.nutritrack.ui.theme.warningColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

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
    onSuggest: (String) -> Unit,
    onWhatToEat: (Int?) -> Unit,
) {
    val loaded by remember(date) { Graph.app.db.diary().observeDay(date.toString()) }
        .collectAsStateWithLifecycle(initialValue = null)
    val entries = loaded.orEmpty()
    val dates by remember { Graph.app.db.diary().observeLoggedDates() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val streak = remember(dates) { Progress.streak(dates, LocalDate.now()) }
    val recent by remember(date) { Graph.app.db.diary().observeRange(date.minusDays(30).toString(), date.toString()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

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

    // Celebrate the moment a goal is reached.
    val snackbar = remember { SnackbarHostState() }
    var confetti by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(loaded, settings) {
        val list = loaded ?: return@LaunchedEffect
        val targets = Targets.compute(settings)
        val totals = sumNutrients(list.map { NutrientCodec.decode(it.nutrients) })
        val met = Progress.goalsMet(totals, targets)
        val newly = Progress.newlyMet(date.toString(), met)
        if (newly.isNotEmpty()) {
            confetti++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val allMet = met.containsAll(Progress.celebrated)
            snackbar.showSnackbar(Progress.celebrationMessage(newly, allMet))
        }
    }

    Box(Modifier.fillMaxSize()) {
        DiaryContent(
            date = date,
            greeting = if (date == LocalDate.now()) greeting(LocalTime.now().hour) else null,
            settings = settings,
            entries = entries,
            recentEntries = recent,
            now = LocalDateTime.now(),
            streak = streak,
            plan = plan,
            planError = planError,
            snackbar = snackbar,
            onDateChange = onDateChange,
            onAdd = onAdd,
            onOpenEntry = onOpenEntry,
            onShowNutrients = onShowNutrients,
            onSetupProfile = onSetupProfile,
            onOpenMealPlan = onOpenMealPlan,
            onSuggest = onSuggest,
            onWhatToEat = onWhatToEat,
        )
        Confetti(trigger = confetti)
    }
}

fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning ☀️"
    in 12..16 -> "Good afternoon 🌤️"
    in 17..21 -> "Good evening 🌙"
    else -> "Hey, night owl 🦉"
}

/** The diary's look, kept free of data loading so it can be previewed and screenshot-tested. */
@Composable
fun DiaryContent(
    date: LocalDate,
    greeting: String?,
    settings: Settings,
    entries: List<DiaryEntry>,
    recentEntries: List<DiaryEntry>,
    now: LocalDateTime,
    streak: Int,
    plan: List<MealiePlanEntry>,
    planError: String?,
    onDateChange: (LocalDate) -> Unit,
    onAdd: (Int) -> Unit,
    onOpenEntry: (DiaryEntry) -> Unit,
    onShowNutrients: () -> Unit,
    onSetupProfile: () -> Unit,
    onOpenMealPlan: (MealiePlanEntry) -> Unit,
    onSuggest: (String) -> Unit,
    onWhatToEat: (Int?) -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val totals = remember(entries) { sumNutrients(entries.map { NutrientCodec.decode(it.nutrients) }) }
    val targets = remember(settings) { Targets.compute(settings) }
    val warnings = remember(entries, recentEntries, settings, date, now.hour) {
        if (settings.lowWarnings) LowNutrients.warnings(date, now, entries, recentEntries, targets) else null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { DateTopBar(date = date, onDateChange = onDateChange, title = greeting) },
        snackbarHost = { SnackbarHost(snackbar) },
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
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👋", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Make it yours", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Add your age, sex, height and weight so your targets fit you.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item { SummaryCard(totals, targets, streak, LowNutrients.dayIsJudged(date, now)) }
            if (warnings != null && !warnings.isEmpty) {
                item(key = "low") { LowWarningCard(warnings, onSuggest, onWhatToEat) }
            }
            if (plan.isNotEmpty() || planError != null) {
                item { MealPlanCard(plan, planError, onOpenMealPlan) }
            }
            if (entries.isNotEmpty() && (warnings == null || warnings.isEmpty)) {
                boostTarget(totals, targets)?.let { (id, percent) ->
                    item(key = "boost") { BoostCard(id, percent, onSuggest, onWhatToEat) }
                }
            }
            item { HighlightsCard(totals, targets, warnings?.today?.map { it.id }?.toSet().orEmpty(), onShowNutrients) }
            Meals.names.indices.forEach { meal ->
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
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateLabel(date), style = MaterialTheme.typography.titleLarge)
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

private fun statusLine(eaten: Double, target: Double, evening: Boolean): String {
    val f = if (target > 0) eaten / target else 0.0
    return when {
        eaten <= 0.0 -> if (evening) "Nothing logged yet today" else "Fresh start. Log your first bite!"
        f < 0.5 && evening -> "A light day so far. Still time for a good meal"
        f < 0.5 -> "Off to a good start"
        f < 0.95 -> "Keep it rolling"
        f <= 1.05 -> "Right on target. Nice!"
        else -> "A bit over today, no stress"
    }
}

@Composable
private fun SummaryCard(totals: Map<Int, Double>, targets: Map<Int, Double>, streak: Int, evening: Boolean) {
    val eaten = totals[Nutrients.ENERGY] ?: 0.0
    val target = targets[Nutrients.ENERGY] ?: 2000.0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalorieRing(eaten = eaten, target = target)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreakPill(streak)
                    Stat("🍽️ Eaten", "${fmtKcal(eaten)} kcal")
                    Stat("🎯 Target", "${fmtKcal(target)} kcal")
                }
            }
            Text(
                statusLine(eaten, target, evening),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroColumn("💪", "Protein", totals[Nutrients.PROTEIN] ?: 0.0, targets[Nutrients.PROTEIN], NutrientGroup.PROTEIN.color(), Modifier.weight(1f))
                MacroColumn("🍞", "Carbs", totals[Nutrients.CARBS] ?: 0.0, targets[Nutrients.CARBS], NutrientGroup.CARBS.color(), Modifier.weight(1f))
                MacroColumn("🥑", "Fat", totals[Nutrients.FAT] ?: 0.0, targets[Nutrients.FAT], NutrientGroup.LIPIDS.color(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StreakPill(streak: Int) {
    val text = when (streak) {
        0 -> "🔥 Start a streak"
        1 -> "🔥 1 day streak"
        else -> "🔥 $streak day streak"
    }
    val (bg, fg) = streakColors()
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (streak > 0) fg else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (streak > 0) bg else trackColor())
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** The key nutrient furthest from its goal (under 60%), if there's a food tip for it. */
private fun boostTarget(totals: Map<Int, Double>, targets: Map<Int, Double>): Pair<Int, Int>? =
    Progress.celebrated
        .filter { Nutrients.byId[it]?.kind == TargetKind.GOAL && Emoji.boosters(it).isNotEmpty() }
        .mapNotNull { id -> targets[id]?.takeIf { it > 0 }?.let { id to ((totals[id] ?: 0.0) / it * 100).toInt() } }
        .filter { it.second < 60 }
        .minByOrNull { it.second }

/** "Running low" heads-up: what's low today (in the evening) and over the past week, with foods that help. */
@Composable
private fun LowWarningCard(w: LowWarnings, onSuggest: (String) -> Unit, onWhatToEat: (Int?) -> Unit) {
    val colors = warningColors()
    val tipFor = w.all.firstOrNull { Emoji.boosters(it.id).isNotEmpty() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Running low", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            w.today.isNotEmpty() && w.week.isNotEmpty() -> "Under half your target today, or low all week"
                            w.today.isNotEmpty() -> "Under half your target with the day almost done"
                            else -> "Averaging under 70% over your last ${w.weekDays} logged days"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            w.all.take(6).forEach { low ->
                val todayLow = w.today.any { it.id == low.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = Emoji.boosters(low.id).isNotEmpty()) { onSuggest(Emoji.boosters(low.id).first().second) }
                        .padding(vertical = 5.dp),
                ) {
                    Text(Emoji.nutrient(low.id), modifier = Modifier.width(30.dp))
                    Text(low.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (todayLow) "${low.percent}% today" else "${low.percent}% avg this week",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                }
            }
            if (w.all.size > 6) {
                Text("+ ${w.all.size - 6} more on the Nutrients tab", style = MaterialTheme.typography.bodySmall)
            }
            if (tipFor != null) {
                Spacer(Modifier.height(8.dp))
                Text("Foods rich in ${tipFor.name}:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Emoji.boosters(tipFor.id).forEach { (emoji, food) ->
                        AssistChip(onClick = { onSuggest(food) }, label = { Text("$emoji $food") })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onWhatToEat(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("🍽️ Show me what to eat")
            }
        }
    }
}

@Composable
private fun BoostCard(id: Int, percent: Int, onSuggest: (String) -> Unit, onWhatToEat: (Int?) -> Unit) {
    val def = Nutrients.byId[id] ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Emoji.nutrient(id), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Boost your ${def.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "You're at $percent% today. These are packed with it:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Emoji.boosters(id).forEach { (emoji, food) ->
                    AssistChip(onClick = { onSuggest(food) }, label = { Text("$emoji $food") })
                }
            }
            TextButton(onClick = { onWhatToEat(id) }) { Text("🍽️ More ideas for ${def.name}") }
        }
    }
}

@Composable
private fun MealPlanCard(plan: List<MealiePlanEntry>, error: String?, onOpen: (MealiePlanEntry) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "📅 On the menu (Mealie)",
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
                    EmojiTile(Emoji.food(e.displayName, source = FoodSource.RECIPE), mealColor(e.meal), size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            e.entryType.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e.recipeSlug != null) {
                        Text("Log it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightsCard(totals: Map<Int, Double>, targets: Map<Int, Double>, lowIds: Set<Int>, onShowAll: () -> Unit) {
    val met = Nutrients.highlights.count { id -> targets[id]?.let { t -> t > 0 && (totals[id] ?: 0.0) >= t } == true }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("✨ Key nutrients", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$met of ${Nutrients.highlights.size} goals hit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onShowAll) { Text("All ${Nutrients.all.size}") }
            }
            Nutrients.highlights.mapNotNull { Nutrients.byId[it] }.forEach { def ->
                NutrientRow(def, totals[def.id] ?: 0.0, targets[def.id], null, low = def.id in lowIds)
            }
        }
    }
}

private fun mealTagline(meal: Int): String = when (meal) {
    0 -> "Rise and dine"
    1 -> "Midday fuel stop"
    2 -> "What's cooking tonight?"
    else -> "Snack attack? No judgment"
}

@Composable
private fun MealCard(meal: Int, entries: List<DiaryEntry>, onAdd: () -> Unit, onOpen: (DiaryEntry) -> Unit) {
    val kcal = entries.sumOf { NutrientCodec.decode(it.nutrients)[Nutrients.ENERGY] ?: 0.0 }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 10.dp),
            ) {
                EmojiTile(Emoji.meal(meal), mealColor(meal), size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Meals.name(meal), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (entries.isEmpty()) mealTagline(meal) else "${fmtKcal(kcal)} kcal · ${entries.size} ${if (entries.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "Add to ${Meals.name(meal)}")
                }
            }
            if (entries.isNotEmpty()) {
                HorizontalDivider(
                    Modifier.padding(top = 10.dp, bottom = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            entries.forEach { e ->
                val eKcal = NutrientCodec.decode(e.nutrients)[Nutrients.ENERGY] ?: 0.0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(e) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    EmojiTile(Emoji.food(e.name, source = FoodSource.of(e.source)), foodTileColor(e.name), size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (e.unitLabel == "g") "${fmtAmount(e.grams)} g" else "${fmtQty(e.quantity)} ${e.unitLabel} · ${fmtAmount(e.grams)} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(fmtKcal(eKcal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
