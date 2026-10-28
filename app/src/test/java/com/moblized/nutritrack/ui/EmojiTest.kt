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
