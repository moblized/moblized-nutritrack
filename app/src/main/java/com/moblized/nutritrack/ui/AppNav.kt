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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Settings
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate

private const val CREATED_KEY = "created_food"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("diary", "Diary", Icons.Filled.Restaurant),
    Tab("nutrients", "Nutrients", Icons.Filled.BarChart),
    Tab("foods", "My foods", Icons.Filled.Kitchen),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

object Routes {
    fun add(date: LocalDate, meal: Int) = "add/$date/$meal"
    fun food(source: FoodSource, id: Long, date: LocalDate, meal: Int, entryId: Long = 0) =
        "food/${source.key}/$id?date=$date&meal=$meal&entry=$entryId"
    fun custom(id: Long, barcode: String? = null) =
        "custom/$id" + (barcode?.let { "?barcode=" + URLEncoder.encode(it, "UTF-8") } ?: "")
    fun recipe(id: Long) = "recipe/$id"
    const val TARGETS = "targets"
    const val MEALIE = "mealie"
    fun mealieImport(slug: String, date: LocalDate?, meal: Int) =
        "mealie/import/" + android.net.Uri.encode(slug) + "?date=" + (date?.toString() ?: "") + "&meal=$meal"
}

@Composable
fun AppNav(settings: Settings) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateText)
    val setDate: (LocalDate) -> Unit = { dateText = it.toString() }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

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
                    "add/{date}/{meal}",
                    arguments = listOf(
                        navArgument("date") { type = NavType.StringType },
                        navArgument("meal") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val d = LocalDate.parse(entry.arguments?.getString("date") ?: LocalDate.now().toString())
                    val meal = entry.arguments?.getInt("meal") ?: 0
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
                        onBack = { nav.popBackStack() },
                        onOpenFood = { source, id -> nav.navigate(Routes.food(source, id, d, meal)) },
                        onCreateFood = { barcode -> nav.navigate(Routes.custom(0, barcode)) },
                        onCreateRecipe = { nav.navigate(Routes.recipe(0)) },
                    )
                }
                composable(
                    "food/{source}/{id}?date={date}&meal={meal}&entry={entry}",
                    arguments = listOf(
                        navArgument("source") { type = NavType.StringType },
                        navArgument("id") { type = NavType.LongType },
                        navArgument("date") { type = NavType.StringType; defaultValue = "" },
                        navArgument("meal") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("entry") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    val d = args?.getString("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: date
                    FoodDetailScreen(
                        source = FoodSource.of(args?.getString("source") ?: "usda"),
                        foodId = args?.getLong("id") ?: 0L,
                        date = d,
                        initialMeal = args?.getInt("meal") ?: 0,
                        entryId = args?.getLong("entry") ?: 0L,
                        settings = settings,
                        onBack = { nav.popBackStack() },
                        onSaved = {
                            // Return to the diary after logging.
                            if (!nav.popBackStack("diary", inclusive = false)) nav.popBackStack()
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
                    val id = entry.arguments?.getLong("id") ?: 0L
                    CustomFoodScreen(
                        foodId = id,
                        barcode = entry.arguments?.getString("barcode")?.takeIf { it.isNotBlank() },
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
                    val id = entry.arguments?.getLong("id") ?: 0L
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
                    val slug = entry.arguments?.getString("slug").orEmpty()
                    val logDate = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                    val meal = entry.arguments?.getInt("meal") ?: 0
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
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
