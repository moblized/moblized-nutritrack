package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Meals
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.now
import com.moblized.nutritrack.data.urlEncode
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private const val CREATED_KEY = "created_food"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("diary", "Diary", Icons.Filled.Restaurant),
    Tab("nutrients", "Nutrients", Icons.Filled.BarChart),
    Tab("eat", "Ideas", Icons.Filled.TipsAndUpdates),
    Tab("foods", "My foods", Icons.Filled.Kitchen),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

object Routes {
    fun add(date: LocalDate, meal: Int, query: String? = null) =
        "add/$date/$meal" + (query?.let { "?q=" + urlEncode(it) } ?: "")
    fun food(
        source: FoodSource, id: Long, date: LocalDate, meal: Int, entryId: Long = 0,
        unit: String? = null, unitGrams: Double? = null, qty: Double? = null,
    ) = "food/${source.key}/$id?date=$date&meal=$meal&entry=$entryId" +
        (if (unit != null) "&unit=" + urlEncode(unit) + "&ug=${unitGrams ?: 1.0}&qty=${qty ?: 1.0}" else "")
    fun eat(focus: Int? = null) = "eat" + (focus?.let { "?focus=$it" } ?: "")
    fun custom(id: Long, barcode: String? = null) =
        "custom/$id" + (barcode?.let { "?barcode=" + urlEncode(it) } ?: "")
    fun recipe(id: Long) = "recipe/$id"
    const val TARGETS = "targets"
    const val MEALIE = "mealie"
    fun mealieImport(slug: String, date: LocalDate?, meal: Int) =
        "mealie/import/" + urlEncode(slug) + "?date=" + (date?.toString() ?: "") + "&meal=$meal"
}

@Composable
fun AppNav(settings: Settings, startRoute: String = "diary") {
    val nav = rememberNavController()
    LaunchedEffect(Unit) {
        when (startRoute) {
            "diary" -> Unit
            "search" -> nav.navigate(Routes.add(LocalDate.now(), 0, "greek yogurt"))
            else -> nav.switchTab(startRoute)
        }
    }
    val scope = rememberCoroutineScope()
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateText)
    val setDate: (LocalDate) -> Unit = { dateText = it.toString() }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route?.substringBefore("?") }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = "diary") {
                composable("diary") {
                    DiaryScreen(
                        date = date,
                        onDateChange = setDate,
                        settings = settings,
                        onAdd = { meal -> nav.navigate(Routes.add(date, meal)) },
                        onOpenEntry = { e -> nav.navigate(Routes.food(FoodSource.of(e.source), e.refId, date, e.meal, e.id)) },
                        onShowNutrients = { nav.switchTab("nutrients") },
                        onSetupProfile = { nav.switchTab("settings") },
                        onOpenMealPlan = { e ->
                            val slug = e.recipeSlug
                            if (slug != null) {
                                scope.launch {
                                    val linked = Graph.app.db.recipes().byMealieSlug(slug)
                                    if (linked != null) {
                                        nav.navigate(Routes.food(FoodSource.RECIPE, linked.id, date, e.meal))
                                    } else {
                                        nav.navigate(Routes.mealieImport(slug, date, e.meal))
                                    }
                                }
                            }
                        },
                        onWhatToEat = { focus -> nav.navigate(Routes.eat(focus)) },
                        onSuggest = { food ->
                            val meal = if (date == LocalDate.now()) Meals.forHour(LocalTime.now().hour) else 3
                            nav.navigate(Routes.add(date, meal, food))
                        },
                    )
                }
                composable(
                    "eat?focus={focus}",
                    arguments = listOf(navArgument("focus") { type = NavType.IntType; defaultValue = 0 }),
                ) { entry ->
                    WhatToEatScreen(
                        date = date,
                        onDateChange = setDate,
                        settings = settings,
                        initialFocus = entry.int("focus")?.takeIf { it > 0 },
                        onAdd = { c ->
                            nav.navigate(
                                Routes.food(c.source, c.id, date, suggestedMeal(date), unit = c.unitLabel, unitGrams = c.unitGrams, qty = c.quantity),
                            )
                        },
                    )
                }
                composable("nutrients") {
                    NutrientsScreen(date = date, onDateChange = setDate, settings = settings)
                }
                composable("foods") {
                    FoodsScreen(
                        onOpenRecipe = { nav.navigate(Routes.recipe(it)) },
                        onOpenCustom = { nav.navigate(Routes.custom(it)) },
                        onImportMealie = if (settings.mealieConfigured) ({ nav.navigate(Routes.MEALIE) }) else null,
                    )
                }
                composable("settings") {
                    SettingsScreen(settings = settings, onOpenTargets = { nav.navigate(Routes.TARGETS) })
                }
                composable(
                    "add/{date}/{meal}?q={q}",
                    arguments = listOf(
                        navArgument("date") { type = NavType.StringType },
                        navArgument("meal") { type = NavType.IntType },
                        navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val d = LocalDate.parse(entry.str("date") ?: LocalDate.now().toString())
                    val meal = entry.int("meal") ?: 0
                    // A food or recipe created from this screen opens straight away so it can be logged.
                    val created by entry.savedStateHandle.getStateFlow(CREATED_KEY, "").collectAsState()
                    LaunchedEffect(created) {
                        val parts = created.split(':')
                        val id = parts.getOrNull(1)?.toLongOrNull()
                        if (parts.size == 2 && id != null && id > 0) {
                            entry.savedStateHandle[CREATED_KEY] = ""
                            nav.navigate(Routes.food(FoodSource.of(parts[0]), id, d, meal))
                        }
                    }
                    AddFoodScreen(
                        date = d,
                        meal = meal,
                        initialQuery = entry.str("q").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenFood = { source, id -> nav.navigate(Routes.food(source, id, d, meal)) },
                        onCreateFood = { barcode -> nav.navigate(Routes.custom(0, barcode)) },
                        onCreateRecipe = { nav.navigate(Routes.recipe(0)) },
                    )
                }
                composable(
                    "food/{source}/{id}?date={date}&meal={meal}&entry={entry}&unit={unit}&ug={ug}&qty={qty}",
                    arguments = listOf(
                        navArgument("source") { type = NavType.StringType },
                        navArgument("id") { type = NavType.LongType },
                        navArgument("date") { type = NavType.StringType; defaultValue = "" },
                        navArgument("meal") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("entry") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("unit") { type = NavType.StringType; defaultValue = "" },
                        navArgument("ug") { type = NavType.StringType; defaultValue = "" },
                        navArgument("qty") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val args = entry
                    val d = args.str("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: date
                    FoodDetailScreen(
                        source = FoodSource.of(args.str("source") ?: "usda"),
                        foodId = args.long("id") ?: 0L,
                        date = d,
                        initialMeal = args.int("meal") ?: 0,
                        entryId = args.long("entry") ?: 0L,
                        settings = settings,
                        presetUnit = args.str("unit")?.takeIf { it.isNotBlank() },
                        presetUnitGrams = args.str("ug")?.toDoubleOrNull(),
                        presetQty = args.str("qty")?.toDoubleOrNull(),
                        onBack = { nav.popBackStack() },
                        onSaved = {
                            val from = nav.previousBackStackEntry?.destination?.route.orEmpty()
                            if (from.startsWith("eat")) {
                                // Back to the suggestions, which update for what's still missing.
                                nav.popBackStack()
                            } else if (!nav.popBackStack("diary", inclusive = false)) {
                                // Otherwise return to the diary after logging.
                                nav.popBackStack()
                            }
                        },
                        onEditFood = { source, id ->
                            when (source) {
                                FoodSource.CUSTOM -> nav.navigate(Routes.custom(id))
                                FoodSource.RECIPE -> nav.navigate(Routes.recipe(id))
                                FoodSource.USDA -> Unit
                            }
                        },
                    )
                }
                composable(
                    "custom/{id}?barcode={barcode}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val id = entry.long("id") ?: 0L
                    CustomFoodScreen(
                        foodId = id,
                        barcode = entry.str("barcode")?.takeIf { it.isNotBlank() },
                        onSaved = { savedId ->
                            if (id == 0L) nav.previousBackStackEntry?.savedStateHandle?.set(CREATED_KEY, "custom:$savedId")
                            nav.popBackStack()
                        },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(
                    "recipe/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.long("id") ?: 0L
                    RecipeScreen(
                        recipeId = id,
                        onSaved = { savedId ->
                            if (id == 0L) nav.previousBackStackEntry?.savedStateHandle?.set(CREATED_KEY, "recipe:$savedId")
                            nav.popBackStack()
                        },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(Routes.MEALIE) {
                    MealieBrowseScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { slug -> nav.navigate(Routes.mealieImport(slug, null, 0)) },
                    )
                }
                composable(
                    "mealie/import/{slug}?date={date}&meal={meal}",
                    arguments = listOf(
                        navArgument("slug") { type = NavType.StringType },
                        navArgument("date") { type = NavType.StringType; defaultValue = "" },
                        navArgument("meal") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) { entry ->
                    val slug = entry.str("slug").orEmpty()
                    val logDate = entry.str("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                    val meal = entry.int("meal") ?: 0
                    MealieImportScreen(
                        slug = slug,
                        onBack = { nav.popBackStack() },
                        onImported = { recipeId ->
                            nav.popBackStack()
                            // Opened from the meal plan: go straight to logging it.
                            if (logDate != null) nav.navigate(Routes.food(FoodSource.RECIPE, recipeId, logDate, meal))
                        },
                    )
                }
                composable(Routes.TARGETS) {
                    TargetsScreen(settings = settings, onBack = { nav.popBackStack() })
                }
            }
        }
        if (showBar) {
            AppBottomBar(route) { nav.switchTab(it) }
        }
    }
    UpdatePrompt()
}

@Composable
fun AppBottomBar(route: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = route?.substringBefore("?") == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, maxLines = 1, softWrap = false) },
            )
        }
    }
}

// Navigation arguments (SavedState on both phones).
private fun NavBackStackEntry.str(key: String): String? = arguments?.read { if (contains(key)) getString(key) else null }
private fun NavBackStackEntry.int(key: String): Int? = arguments?.read { if (contains(key)) getInt(key) else null }
private fun NavBackStackEntry.long(key: String): Long? = arguments?.read { if (contains(key)) getLong(key) else null }

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
