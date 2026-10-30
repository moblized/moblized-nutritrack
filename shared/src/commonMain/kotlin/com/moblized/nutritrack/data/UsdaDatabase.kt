package com.moblized.nutritrack.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Read-only access to the bundled USDA FoodData Central database (usda.db). [path] gives the file:
 * Android copies it out of the APK's assets, the iPhone app reads it straight from the app bundle.
 */
class UsdaDatabase(private val path: () -> String) : FoodCandidateSource {
    private val lock = SynchronizedObject()
    private var connection: SQLiteConnection? = null

    /** Runs [block] on the one shared connection (SQLite connections aren't thread-safe). */
    private fun <T> query(sql: String, vararg args: Any, block: (SQLiteStatement) -> T): T = synchronized(lock) {
        val c = connection ?: BundledSQLiteDriver().open(path(), SQLITE_OPEN_READONLY).also { connection = it }
        c.prepare(sql).use { st ->
            args.forEachIndexed { i, a ->
                when (a) {
                    is Long -> st.bindLong(i + 1, a)
                    is Int -> st.bindLong(i + 1, a.toLong())
                    else -> st.bindText(i + 1, a.toString())
                }
            }
            block(st)
        }
    }

    fun search(query: String, limit: Int = 60): List<FoodSummary> {
        val tokens = query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() && it !in STOP_WORDS }
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.joinToString(" ") { "$it*" }
        val sql = """
            SELECT f.id, f.description, f.category, f.source, f.kcal
            FROM food_fts JOIN foods f ON f.id = food_fts.docid
            WHERE food_fts MATCH ?
            ORDER BY (f.description LIKE ?) DESC, length(f.description) ASC, f.n_count DESC
            LIMIT $limit
        """.trimIndent()
        return query(sql, match, tokens.first() + "%") { c ->
            val out = ArrayList<FoodSummary>()
            while (c.step()) {
                out += FoodSummary(
                    source = FoodSource.USDA,
                    id = c.getLong(0),
                    name = c.getText(1),
                    detail = detail(c.textOrNull(2), c.getLong(3).toInt()),
                    kcalPer100 = if (c.isNull(4)) null else c.getDouble(4),
                )
            }
            out
        }
    }

    data class Candidate(val id: Long, val description: String, val source: Int, val portions: List<Portion>)

    /** Search results with their portions, used to auto-match recipe ingredients. */
    override fun candidates(query: String, limit: Int): List<Candidate> {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val sql = """
            SELECT f.id, f.description, f.source, f.portions
            FROM food_fts JOIN foods f ON f.id = food_fts.docid
            WHERE food_fts MATCH ?
            ORDER BY (f.description LIKE ?) DESC, length(f.description) ASC, f.n_count DESC
            LIMIT $limit
        """.trimIndent()
        return query(sql, tokens.joinToString(" ") { "$it*" }, tokens.first() + "%") { c ->
            val out = ArrayList<Candidate>()
            while (c.step()) {
                out += Candidate(c.getLong(0), c.getText(1), c.getLong(2).toInt(), parsePortions(c.textOrNull(3)))
            }
            out
        }
    }

    /** Everyday foods with a typical portion for "What to eat" (built from USDA survey foods). */
    fun suggestionPool(): List<SuggestCandidate> {
        val keep = (Suggestions.goals + listOf(Nutrients.ENERGY, Nutrients.SODIUM, Nutrients.SAT_FAT, Nutrients.ADDED_SUGARS)).toSet()
        return runCatching {
            query(
                "SELECT s.id, f.description, f.category, s.label, s.grams, s.grp, f.nutrients FROM suggest s JOIN foods f ON f.id = s.id",
            ) { c ->
                val out = ArrayList<SuggestCandidate>()
                while (c.step()) {
                    val grams = c.getDouble(4)
                    val per100 = NutrientCodec.decode(c.textOrNull(6)).filterKeys { it in keep }
                    out += SuggestCandidate(
                        source = FoodSource.USDA,
                        id = c.getLong(0),
                        name = c.getText(1),
                        category = c.textOrNull(2),
                        group = FoodGroup.of(c.getText(5)),
                        quantity = 1.0,
                        unitLabel = c.getText(3),
                        unitGrams = grams,
                        nutrients = per100.scaled(grams / 100.0),
                    )
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    override fun food(id: Long): Food? =
        query("SELECT description, category, source, nutrients, portions FROM foods WHERE id = ?", id) { c ->
            if (!c.step()) return@query null
            Food(
                source = FoodSource.USDA,
                id = id,
                name = c.getText(0),
                detail = detail(c.textOrNull(1), c.getLong(2).toInt()),
                per100g = NutrientCodec.decode(c.textOrNull(3)),
                portions = parsePortions(c.textOrNull(4)),
            )
        }

    fun foodCount(): Int = runCatching {
        query("SELECT COUNT(*) FROM foods") { c -> if (c.step()) c.getLong(0).toInt() else 0 }
    }.getOrDefault(0)

    private fun SQLiteStatement.textOrNull(i: Int): String? = if (isNull(i)) null else getText(i)

    /** Portions are stored as JSON pairs: [["1 cup", 244.0], ...]. */
    private fun parsePortions(json: String?): List<Portion> {
        val arr = parseJson(json ?: "[]") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val p = el as? JsonArray ?: return@mapNotNull null
            val label = (p.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val grams = (p.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (grams > 0) Portion(label, grams) else null
        }
    }

    private fun detail(category: String?, source: Int): String {
        val src = when (source) {
            0 -> "USDA SR Legacy"
            1 -> "USDA Foundation"
            2 -> "USDA Survey (FNDDS)"
            3 -> "Nutrition label"
            else -> "USDA"
        }
        return if (category.isNullOrBlank()) src else "$category · $src"
    }

    companion object {
        const val DB_NAME = "usda.db"
        private val STOP_WORDS = setOf("and", "with", "of", "the", "a", "in", "or", "w")
    }
}
