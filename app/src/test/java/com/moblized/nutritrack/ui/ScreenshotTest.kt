package com.moblized.nutritrack.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.moblized.nutritrack.data.DiaryEntry
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.FoodSummary
import com.moblized.nutritrack.data.MealiePlanEntry
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Profile
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.Sex
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.data.sumNutrients
import com.moblized.nutritrack.ui.theme.NutriTheme
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders the main screens with realistic data so the look can be checked without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h1700dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = Settings(
        profile = Profile(sex = Sex.MALE, age = 35, heightCm = 180.0, weightKg = 84.0),
        profileSet = true,
    )

    private fun per100(description: String): Map<Int, Double> {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("usda.db has not been built", db != null)
        // Robolectric's sandboxed class loader can hide JDBC drivers from DriverManager, so use the driver directly.
        org.sqlite.JDBC().connect("jdbc:sqlite:" + db!!.absolutePath, java.util.Properties())!!.use { conn ->
            conn.prepareStatement("SELECT nutrients FROM foods WHERE description = ? LIMIT 1").use { st ->
                st.setString(1, description)
                st.executeQuery().use { rs ->
                    check(rs.next()) { "No food named $description" }
                    return NutrientCodec.decode(rs.getString(1))
                }
            }
        }
    }

    private var nextId = 1L

    private fun entry(meal: Int, usda: String, name: String, qty: Double, unit: String, unitGrams: Double): DiaryEntry {
        val grams = qty * unitGrams
        return DiaryEntry(
            id = nextId++, date = LocalDate.now().toString(), meal = meal, loggedAt = nextId,
            source = FoodSource.USDA.key, refId = nextId, name = name, quantity = qty, unitLabel = unit,
            unitGrams = unitGrams, grams = grams, nutrients = NutrientCodec.encode(per100(usda).scaled(grams / 100.0)),
        )
    }

    private fun sampleDay(): List<DiaryEntry> = listOf(
        entry(0, "Egg, whole, raw, fresh", "Egg, whole, raw, fresh", 2.0, "large", 50.0),
        entry(0, "Oats, whole grain, rolled, old fashioned", "Oats, whole grain, rolled, old fashioned", 0.5, "cup", 80.0),
        entry(0, "Bananas, raw", "Bananas, raw", 1.0, "medium (7\" to 7-7/8\" long)", 118.0),
        entry(1, "Chicken, breast, boneless, skinless, raw", "Chicken, breast, boneless, skinless, raw", 150.0, "g", 1.0),
        entry(1, "Spinach, raw", "Spinach, raw", 2.0, "cup", 30.0),
        entry(3, "Nuts, almonds", "Nuts, almonds", 1.0, "oz", 28.35),
        entry(3, "Yogurt, Greek, whole milk, plain", "Yogurt, Greek, whole milk, plain", 1.0, "cup", 245.0),
    )

    private val plan = listOf(MealiePlanEntry(LocalDate.now().toString(), "dinner", "", "chicken-tikka-masala", "Chicken Tikka Masala"))

    private fun shoot(name: String, dark: Boolean = false, pureBlack: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            NutriTheme(dark = dark, pureBlack = pureBlack) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
        println("SHOT $name")
    }

    @Composable
    private fun diary(
        entries: List<DiaryEntry>,
        s: Settings,
        streak: Int,
        withPlan: Boolean,
        hour: Int = 8,
        recent: List<DiaryEntry> = emptyList(),
    ) = DiaryContent(
        date = LocalDate.now(), greeting = greeting(hour), settings = s, entries = entries,
        recentEntries = recent, now = LocalDate.now().atTime(hour, 0), streak = streak,
        plan = if (withPlan) plan else emptyList(), planError = null,
        onDateChange = {}, onAdd = {}, onOpenEntry = {}, onShowNutrients = {}, onSetupProfile = {},
        onOpenMealPlan = {}, onSuggest = {},
    )

    @Test
    fun diaryLight() {
        val day = sampleDay()
        shoot("1_diary_light") { diary(day, settings, streak = 6, withPlan = true) }
    }

    /** Evening view with a low day and a low week, so the "Running low" card shows. */
    @Test
    fun diaryEveningWarnings() {
        val day = sampleDay().filter { it.meal != 3 }
        val week = (1L..4L).flatMap { back ->
            day.map { it.copy(id = it.id + back * 100, date = LocalDate.now().minusDays(back).toString()) }
        }
        shoot("7_diary_evening_warnings") { diary(day, settings, streak = 5, withPlan = false, hour = 19, recent = week) }
    }

    @Test
    fun diaryDarkPureBlack() {
        val day = sampleDay()
        shoot("2_diary_dark", dark = true, pureBlack = true) { diary(day, settings, streak = 6, withPlan = false) }
    }

    /** Phone-sized: the "Add to Lunch" button sits over the list at lunchtime... */
    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun diaryAddButton() {
        val day = sampleDay()
        shoot("11_diary_add_button") { diary(day, settings, streak = 6, withPlan = true, hour = 12) }
    }

    /** ...and shrinks to a + once you scroll down to the meals. */
    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun diaryAddButtonScrolled() {
        val day = sampleDay()
        compose.setContent {
            NutriTheme(dark = false, pureBlack = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    diary(day, settings, streak = 6, withPlan = true, hour = 12)
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/12_diary_add_button_scrolled.png")
        println("SHOT 12_diary_add_button_scrolled")
    }

    @Test
    fun diaryNewUser() {
        shoot("3_diary_new_user") { diary(emptyList(), Settings(), streak = 0, withPlan = false) }
    }

    @Test
    @Config(qualifiers = "w412dp-h3400dp-xhdpi")
    fun nutrients() {
        val totals = sumNutrients(sampleDay().map { NutrientCodec.decode(it.nutrients) })
        val targets = Targets.compute(settings)
        shoot("4_nutrients_light") {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                TargetsMetCard(met = 21, total = 44, subtitle = "1,480 kcal from 7 foods")
                NutrientBreakdown(totals, targets, Targets.upperLimits(settings), hideEmptyInfo = false)
            }
        }
    }

    @Test
    fun searchResults() {
        fun usda(name: String, category: String, kcal: Double) =
            FoodSummary(FoodSource.USDA, name.hashCode().toLong(), name, "$category · USDA SR Legacy", kcal)
        val rows = listOf(
            usda("Bananas, raw", "Fruits and Fruit Juices", 89.0),
            usda("Cheese, Cheddar", "Dairy and Egg Products", 409.0),
            usda("Soup, chicken broth, ready-to-serve", "Soups, Sauces, and Gravies", 6.0),
            usda("Beverages, coffee, brewed, prepared with tap water", "Beverages", 1.0),
            usda("Peppers, bell, red, raw", "Vegetables and Vegetable Products", 26.0),
            usda("Fish, salmon, Atlantic, farmed, raw", "Finfish and Shellfish Products", 208.0),
            usda("Sweet potato, NFS", "Vegetables and Vegetable Products", 86.0),
            usda("Pizza, cheese, from restaurant or fast food, thin crust", "Pizza", 276.0),
            FoodSummary(FoodSource.RECIPE, 1, "Grandma's lentil soup", "Recipe", 92.0),
            FoodSummary(FoodSource.CUSTOM, 2, "Protein bar, chocolate peanut", "Scanned", 380.0),
        )
        shoot("5_search_dark", dark = true) {
            Column { rows.forEach { FoodResultRow(it) {} } }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h200dp-xhdpi")
    fun launcherIcons() {
        shoot("6_launcher_icons") {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                listOf(
                    com.moblized.nutritrack.R.drawable.ic_launcher_foreground to androidx.compose.ui.graphics.Color(0xFFE3F4E9),
                    com.moblized.nutritrack.R.drawable.ic_launcher_foreground_dark to androidx.compose.ui.graphics.Color(0xFF0E1B15),
                    com.moblized.nutritrack.R.drawable.ic_launcher_monochrome to androidx.compose.ui.graphics.Color(0xFFD7E3DB),
                ).forEach { (res, bg) ->
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(res),
                        contentDescription = null,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(bg),
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2600dp-xhdpi")
    fun whatToEat() {
        val day = sampleDay().filter { it.meal != 3 }
        val pool = com.moblized.nutritrack.data.TestFoods.pool()
        val mine = sampleDay().filter { it.meal == 3 }.map {
            com.moblized.nutritrack.data.SuggestCandidate(
                FoodSource.USDA, it.refId, it.name, null, com.moblized.nutritrack.data.FoodGroup.MINE,
                it.quantity, it.unitLabel, it.unitGrams, NutrientCodec.decode(it.nutrients),
            )
        }
        shoot("8_what_to_eat") {
            WhatToEatContent(
                date = LocalDate.now(), onDateChange = {}, settings = settings, entries = day,
                pool = pool, mine = mine, focus = null, onFocus = {}, onAdd = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2000dp-xhdpi")
    fun whatToEatFocused() {
        val day = sampleDay().filter { it.meal != 3 }
        val pool = com.moblized.nutritrack.data.TestFoods.pool()
        shoot("9_what_to_eat_vitamin_d", dark = true) {
            WhatToEatContent(
                date = LocalDate.now(), onDateChange = {}, settings = settings, entries = day,
                pool = pool, mine = emptyList(), focus = com.moblized.nutritrack.data.Nutrients.VIT_D,
                onFocus = {}, onAdd = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h120dp-xhdpi")
    fun bottomBar() {
        shoot("10_bottom_bar") {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom) { AppBottomBar("eat") {} }
        }
    }
}
