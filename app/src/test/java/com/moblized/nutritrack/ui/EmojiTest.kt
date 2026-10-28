package com.moblized.nutritrack.ui

import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate

class EmojiTest {

    @Test
    fun picksEmojiFromFoodNames() {
        val cases = mapOf(
            "Bananas, raw" to "🍌",
            "Soup, chicken broth, ready-to-serve" to "🍲",
            "Chicken, breast, boneless, skinless, raw" to "🍗",
            "Beverages, coffee, brewed" to "☕",
            "Cream cheese, full fat, block" to "🧀",
            "Peppers, bell, red, raw" to "🫑",
            "Spices, pepper, black" to "🧂",
            "Sweet potato, NFS" to "🍠",
            "Nuts, almonds" to "🌰",
            "Egg, whole, raw, fresh" to "🥚",
            "Eggplant, raw" to "🍆",
            "Peaches, raw" to "🍑",
            "Hummus, commercial" to "🫘",
            "Tomatoes, cherry, raw" to "🍅",
            "Cherry tomatoes" to "🍅",
            "Yogurt, Greek, whole milk, plain" to "🥛",
            "Ice cream, vanilla" to "🍨",
            "Peanut butter, smooth" to "🥜",
            "Oats, whole grain, rolled, old fashioned" to "🥣",
            "Sandwiches, turkey" to "🥪",
        )
        cases.forEach { (name, emoji) -> assertEquals(name, emoji, Emoji.food(name)) }
        assertEquals("🍲", Emoji.food("Grandma's special", source = FoodSource.RECIPE))
        assertEquals("🏷️", Emoji.food("Mystery bar", source = FoodSource.CUSTOM))
        assertEquals("🥛", Emoji.food("Something odd", category = "Dairy and Egg Products"))
    }

    @Test
    fun mostUsdaFoodsGetAnIcon() {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue(db != null)
        var total = 0
        var plain = 0
        val counts = HashMap<String, Int>()
        DriverManager.getConnection("jdbc:sqlite:" + db!!.absolutePath).use { conn ->
            conn.createStatement().executeQuery("SELECT description, category FROM foods").use { rs ->
                while (rs.next()) {
                    val e = Emoji.food(rs.getString(1), rs.getString(2), FoodSource.USDA)
                    total++
                    if (e == "🍽️") plain++
                    counts[e] = (counts[e] ?: 0) + 1
                }
            }
        }
        println("EMOJI coverage: ${total - plain} of $total foods have a specific icon; top: " +
            counts.entries.sortedByDescending { it.value }.take(12).joinToString { "${it.key}${it.value}" })
        assertTrue("too many foods without an icon: $plain of $total", plain < total * 0.15)
    }

    @Test
    fun countsStreaks() {
        val today = LocalDate.of(2026, 10, 28)
        val d = { n: Long -> today.minusDays(n).toString() }
        assertEquals(0, Progress.streak(emptyList(), today))
        assertEquals(3, Progress.streak(listOf(d(0), d(1), d(2), d(4)), today))
        // Today not logged yet: the streak through yesterday still counts.
        assertEquals(2, Progress.streak(listOf(d(1), d(2)), today))
        assertEquals(0, Progress.streak(listOf(d(2), d(3)), today))
    }

    @Test
    fun celebratesOnlyNewGoals() {
        val day = "2026-10-28"
        assertEquals(emptySet<Int>(), Progress.newlyMet(day, setOf(1)))
        assertEquals(setOf(2), Progress.newlyMet(day, setOf(1, 2)))
        assertEquals(emptySet<Int>(), Progress.newlyMet(day, setOf(1, 2)))
    }
}

class LowNutrientsTest {
    private val targets = mapOf(
        com.moblized.nutritrack.data.Nutrients.VIT_D to 15.0,
        com.moblized.nutritrack.data.Nutrients.FIBER to 38.0,
        com.moblized.nutritrack.data.Nutrients.IODINE to 150.0,
    )

    private fun entry(date: String, vitD: Double, fiber: Double) = com.moblized.nutritrack.data.DiaryEntry(
        date = date, meal = 0, loggedAt = 0, source = "usda", refId = 1, name = "x", quantity = 1.0,
        unitLabel = "g", unitGrams = 1.0, grams = 1.0,
        nutrients = com.moblized.nutritrack.data.NutrientCodec.encode(
            mapOf(com.moblized.nutritrack.data.Nutrients.VIT_D to vitD, com.moblized.nutritrack.data.Nutrients.FIBER to fiber),
        ),
    )

    @Test
    fun judgesTheDayOnlyInTheEvening() {
        val date = LocalDate.of(2026, 10, 28)
        val day = listOf(entry("2026-10-28", vitD = 3.0, fiber = 30.0))
        val morning = com.moblized.nutritrack.data.LowNutrients.warnings(date, date.atTime(9, 0), day, emptyList(), targets)
        assertTrue(morning.isEmpty)
        val evening = com.moblized.nutritrack.data.LowNutrients.warnings(date, date.atTime(19, 0), day, emptyList(), targets)
        // Vitamin D is at 20%; fiber at 79% is fine; iodine is never judged (poor USDA coverage).
        assertEquals(listOf(com.moblized.nutritrack.data.Nutrients.VIT_D), evening.today.map { it.id })
        assertEquals(20, evening.today.first().percent)
        // A past day is always judged.
        val later = com.moblized.nutritrack.data.LowNutrients.warnings(date, date.plusDays(1).atTime(9, 0), day, emptyList(), targets)
        assertEquals(1, later.today.size)
    }

    @Test
    fun averagesTheWeekBeforeTheDay() {
        val date = LocalDate.of(2026, 10, 28)
        val recent = listOf(
            entry("2026-10-27", vitD = 6.0, fiber = 20.0),
            entry("2026-10-26", vitD = 6.0, fiber = 40.0),
            entry("2026-10-25", vitD = 6.0, fiber = 40.0),
            entry("2026-10-28", vitD = 50.0, fiber = 50.0), // the day itself doesn't count toward the week
        )
        val w = com.moblized.nutritrack.data.LowNutrients.warnings(date, date.atTime(9, 0), emptyList(), recent, targets)
        assertEquals(3, w.weekDays)
        // Vitamin D averages 40%; fiber averages 33.3 g of 38 g = 87%.
        assertEquals(listOf(com.moblized.nutritrack.data.Nutrients.VIT_D), w.week.map { it.id })
        // Fewer than 3 logged days: no weekly verdict.
        val short = com.moblized.nutritrack.data.LowNutrients.warnings(date, date.atTime(9, 0), emptyList(), recent.take(2), targets)
        assertTrue(short.week.isEmpty())
    }

    @Test
    fun writesANotification() {
        val w = com.moblized.nutritrack.data.LowWarnings(
            today = listOf(com.moblized.nutritrack.data.LowNutrient(com.moblized.nutritrack.data.Nutrients.VIT_D, 13)),
            week = emptyList(), weekDays = 0,
        )
        val (title, body) = com.moblized.nutritrack.alerts.LowNutrientAlerts.message(w, includeWeek = true)!!
        assertEquals("⚠️ Running low on Vitamin D", title)
        assertTrue(body, body.startsWith("Today: Vitamin D 13%"))
        assertTrue(body, body.contains("salmon"))
        assertEquals(null, com.moblized.nutritrack.alerts.LowNutrientAlerts.message(w.copy(today = emptyList()), includeWeek = true))
    }
}
