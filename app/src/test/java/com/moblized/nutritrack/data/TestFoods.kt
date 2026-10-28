package com.moblized.nutritrack.data

import org.junit.Assume
import java.io.File
import java.sql.Connection

/** Reads the built usda.db with JDBC so tests use the same data as the app. */
object TestFoods {
    private val dbFile: File? =
        listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }

    fun connect(): Connection {
        Assume.assumeTrue("usda.db has not been built", dbFile != null)
        // Use the driver directly: Robolectric's class loader can hide it from DriverManager.
        return org.sqlite.JDBC().connect("jdbc:sqlite:" + dbFile!!.absolutePath, java.util.Properties())!!
    }

    fun per100(description: String): Map<Int, Double> = connect().use { conn ->
        conn.prepareStatement("SELECT nutrients FROM foods WHERE description = ? LIMIT 1").use { st ->
            st.setString(1, description)
            st.executeQuery().use { rs ->
                check(rs.next()) { "No food named $description" }
                NutrientCodec.decode(rs.getString(1))
            }
        }
    }

    /** Same query as UsdaDatabase.suggestionPool(). */
    fun pool(): List<SuggestCandidate> = connect().use { conn ->
        conn.createStatement().executeQuery(
            "SELECT s.id, f.description, f.category, s.label, s.grams, s.grp, f.nutrients FROM suggest s JOIN foods f ON f.id = s.id",
        ).use { rs ->
            val out = ArrayList<SuggestCandidate>()
            while (rs.next()) {
                val grams = rs.getDouble(5)
                out += SuggestCandidate(
                    FoodSource.USDA, rs.getLong(1), rs.getString(2), rs.getString(3), FoodGroup.of(rs.getString(6)),
                    1.0, rs.getString(4), grams, NutrientCodec.decode(rs.getString(7)).scaled(grams / 100.0),
                )
            }
            out
        }
    }

    fun entry(meal: Int, description: String, qty: Double, unit: String, unitGrams: Double, date: String, id: Long): DiaryEntry {
        val grams = qty * unitGrams
        return DiaryEntry(
            id = id, date = date, meal = meal, loggedAt = id, source = FoodSource.USDA.key, refId = id,
            name = description, quantity = qty, unitLabel = unit, unitGrams = unitGrams, grams = grams,
            nutrients = NutrientCodec.encode(per100(description).scaled(grams / 100.0)),
        )
    }
}
