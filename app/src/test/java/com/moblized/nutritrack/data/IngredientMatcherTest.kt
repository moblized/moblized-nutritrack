package com.moblized.nutritrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.abs
import kotlin.math.max

/** Reads the same bundled database the app uses, through JDBC instead of Android's SQLite. */
internal class JdbcSource(private val conn: Connection) : FoodCandidateSource {
    override fun candidates(query: String, limit: Int): List<UsdaDatabase.Candidate> {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val sql = "SELECT f.id, f.description, f.source, f.portions FROM food_fts JOIN foods f ON f.id = food_fts.docid " +
            "WHERE food_fts MATCH ? ORDER BY (f.description LIKE ?) DESC, length(f.description) ASC, f.n_count DESC LIMIT $limit"
        conn.prepareStatement(sql).use { st ->
            st.setString(1, tokens.joinToString(" ") { "$it*" })
            st.setString(2, tokens.first() + "%")
            st.executeQuery().use { rs ->
                val out = ArrayList<UsdaDatabase.Candidate>()
                while (rs.next()) {
                    out += UsdaDatabase.Candidate(rs.getLong(1), rs.getString(2), rs.getInt(3), portions(rs.getString(4)))
                }
                return out
            }
        }
    }

    override fun food(id: Long): Food? {
        conn.prepareStatement("SELECT description, nutrients, portions FROM foods WHERE id = ?").use { st ->
            st.setLong(1, id)
            st.executeQuery().use { rs ->
                if (!rs.next()) return null
                return Food(FoodSource.USDA, id, rs.getString(1), "", NutrientCodec.decode(rs.getString(2)), portions(rs.getString(3)))
            }
        }
    }

    private fun portions(json: String?): List<Portion> =
        Regex("""\["((?:[^"\\]|\\.)*)",(-?[0-9.]+(?:[eE][+-]?[0-9]+)?)]""").findAll(json ?: "").map {
            Portion(it.groupValues[1].replace("\\\"", "\""), it.groupValues[2].toDouble())
        }.toList()
}

class IngredientMatcherTest {

    @Test
    fun parsesCommonRecipeLines() {
        IngredientParser.parse("1 (14.5 oz) can diced tomatoes").let {
            assertEquals(14.5, it.quantity!!, 1e-9)
            assertEquals("oz", it.unit)
            assertEquals("tomato canned", it.query)
        }
        IngredientParser.parse("1 can (13.5 oz) coconut milk").let {
            assertEquals(13.5, it.quantity!!, 1e-9)
            assertEquals("oz", it.unit)
            assertEquals("coconut milk canned", it.query)
        }
        IngredientParser.parse("2 ½ cups all-purpose flour").let {
            assertEquals(2.5, it.quantity!!, 1e-9)
            assertEquals("cup", it.unit)
            assertEquals("all purpose flour", it.query)
        }
        IngredientParser.parse("2-3 tbsp olive oil").let {
            assertEquals(2.5, it.quantity!!, 1e-9)
            assertEquals("tbsp", it.unit)
            assertEquals(UnitKind.VOLUME, it.kind)
        }
        IngredientParser.parse("4 cloves garlic, minced").let {
            assertEquals(4.0, it.quantity!!, 1e-9)
            assertEquals("clove", it.unit)
            assertEquals("garlic", it.query)
        }
        IngredientParser.parse("1 jalapeño, seeded and minced").let {
            assertEquals("jalapeno", it.query)
            assertEquals("seeded and minced", it.note)
        }
        IngredientParser.parse("80 g canned San Marzano tomatoes crushed").let {
            assertTrue(it.canned)
            assertEquals(80.0, it.quantity!!, 1e-9)
        }
        IngredientParser.parse("50 g pepperoni", foodHint = "pepperoni", noteHint = "turkey").let {
            assertEquals("pepperoni", it.food)
            assertEquals("turkey", it.note)
        }
        IngredientParser.parse("1 T sugar").let { assertEquals("tbsp", it.unit) }
        IngredientParser.parse("1 t salt").let { assertEquals("tsp", it.unit) }
        IngredientParser.parse("salt and pepper to taste").let { assertNull(it.quantity) }
        IngredientParser.parse("8 fl oz milk").let {
            assertEquals("fl oz", it.unit)
            assertEquals("milk", it.query)
        }
        // Alternatives: the first choice counts, and a note that is only an alternative is dropped.
        IngredientParser.parse("430 gram 2% High-Protein Milk or 215 g 2% + 215 g fat-free").let {
            assertEquals(430.0, it.quantity!!, 1e-9)
            assertEquals("g", it.unit)
            assertEquals("2% High-Protein Milk", it.food)
        }
        IngredientParser.parse("20 g sweetener", foodHint = "Erythritol/Swerve").let { assertEquals("Erythritol", it.food) }
        IngredientParser.parse("2 cups chicken or vegetable broth").let { assertEquals("chicken broth", it.food) }
        IngredientParser.parse("32 g whey", foodHint = "vanilla whey protein powder", noteHint = "or 1 scoop").let { assertEquals("", it.note) }
        IngredientParser.parse("30 g chips", foodHint = "chocolate chips", noteHint = "spread 1/8 inch thick on parchment paper, then frozen").let {
            assertEquals("", it.note)
        }
        // Drops are a volume; count units can come after the food.
        IngredientParser.parse("12 drops peppermint extract").let {
            assertEquals("drop", it.unit)
            assertEquals(UnitKind.VOLUME, it.kind)
        }
        IngredientParser.parse("8 drops food coloring", unitHint = "drops").let { assertEquals("drop", it.unit) }
        IngredientParser.parse("3 garlic cloves").let {
            assertEquals("clove", it.unit)
            assertEquals("garlic", it.food)
        }
        assertEquals(listOf("pct2", "high", "protein", "milk"), IngredientParser.words("2% High-Protein Milk"))
        assertEquals("clove", IngredientParser.singular("cloves"))
        assertEquals("olive", IngredientParser.singular("olives"))
        assertEquals("leaf", IngredientParser.singular("leaves"))
        // Mealie's own parser results take priority over the text.
        IngredientParser.parse("2 cups flour", quantityHint = 3.0, unitHint = "Cup", foodHint = "bread flour").let {
            assertEquals(3.0, it.quantity!!, 1e-9)
            assertEquals("cup", it.unit)
            assertEquals("bread flour", it.query)
        }
    }

    @Test
    fun matchesRealUsdaFoods() {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("usda.db has not been built", db != null)
        val conn = DriverManager.getConnection("jdbc:sqlite:" + db!!.absolutePath)
        val matcher = IngredientMatcher(JdbcSource(conn))
        val cases = javaClass.getResourceAsStream("/ingredient_cases.tsv")!!.bufferedReader().readLines().filter { it.isNotBlank() }
        val failures = ArrayList<String>()
        for (case in cases) {
            val (text, expectedFood, expectedGrams, noAmount) = case.split('\t')
            val m = matcher.match(MealieIngredient(null, null, null, text))
            val item = m.item
            println("MATCH  $text  ->  ${item?.name}  ${item?.grams?.let { "%.1f g".format(it) }}${if (m.estimated) " (est)" else ""}")
            when {
                item?.name.orEmpty() != expectedFood -> failures += "$text: expected '$expectedFood', got '${item?.name}'"
                noAmount == "0" && abs(item!!.grams - expectedGrams.toDouble()) > max(1.0, expectedGrams.toDouble() * 0.05) ->
                    failures += "$text: expected ${expectedGrams} g, got ${"%.1f".format(item.grams)} g"
            }
        }
        conn.close()
        failures.forEach { println("MISMATCH $it") }
        assertTrue("${failures.size} of ${cases.size} lines differ:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Lines whose unit has no gram weight are flagged instead of guessed (it used to be 3,840 g of extract). */
    @Test
    fun asksInsteadOfGuessingUnknownUnits() {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("usda.db has not been built", db != null)
        val conn = DriverManager.getConnection("jdbc:sqlite:" + db!!.absolutePath)
        val matcher = IngredientMatcher(JdbcSource(conn))
        matcher.match(MealieIngredient(2.0, "leaves", "bay", "2 bay leaves")).let {
            println("MATCH  2 bay leaves  ->  ${it.item?.name}  unconverted=${it.unconverted}")
            assertTrue("bay leaves should ask for an amount", it.unconverted)
        }
        matcher.match(MealieIngredient(8.0, "drop", "Green Food Coloring", "8 drops Green Food Coloring")).let {
            assertTrue((it.item?.grams ?: 99.0) < 1.0)
        }
        // Nothing in the database is called "coloring" except the label food; nothing is called "zorblax" at all.
        assertNull(matcher.match(MealieIngredient(1.0, "cup", "zorblax", "1 cup zorblax")).item)
        conn.close()
    }

    /** A real Mealie recipe whose lines are split into food + note ("pepperoni" + "turkey"). */
    @Test
    fun matchesParsedMealieIngredients() {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("usda.db has not been built", db != null)
        val conn = DriverManager.getConnection("jdbc:sqlite:" + db!!.absolutePath)
        val matcher = IngredientMatcher(JdbcSource(conn))
        val cases = javaClass.getResourceAsStream("/mealie_cases.tsv")!!.bufferedReader().readLines().filter { it.isNotBlank() }
        val failures = ArrayList<String>()
        for (case in cases) {
            val c = case.split('\t')
            val (text, food, note) = Triple(c[0], c[1], c[2])
            val expectedFood = c[3]
            val expectedGrams = c[4].toDouble()
            // Optional: the quantity and unit Mealie's parser sends with the line.
            val quantity = c.getOrNull(6)?.toDoubleOrNull()
            val unit = c.getOrNull(7)?.ifBlank { null }
            val m = matcher.match(MealieIngredient(quantity, unit, food.ifBlank { null }, text, note.ifBlank { null }))
            val item = m.item
            println("MATCH  $text  ->  ${item?.name}  ${item?.grams}")
            if (item?.name != expectedFood) failures += "$text: expected '$expectedFood', got '${item?.name}'"
            else if (abs(item.grams - expectedGrams) > max(1.0, expectedGrams * 0.05)) failures += "$text: expected $expectedGrams g, got ${item.grams} g"
        }
        conn.close()
        failures.forEach { println("MISMATCH $it") }
        assertTrue("${failures.size} of ${cases.size} Mealie lines differ:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
