package com.moblized.nutritrack.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File

/**
 * Read-only access to the bundled USDA FoodData Central database (assets/usda.db).
 * The asset is copied to app storage on first use and whenever the app is updated.
 */
class UsdaDatabase(private val context: Context) {
    @Volatile
    private var db: SQLiteDatabase? = null

    private fun open(): SQLiteDatabase {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            val file = context.getDatabasePath(DB_NAME)
            val prefs = context.getSharedPreferences("usda", Context.MODE_PRIVATE)
            @Suppress("DEPRECATION")
            val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            if (!file.exists() || prefs.getLong("stamp", -1L) != stamp) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$DB_NAME.tmp")
                context.assets.open(DB_NAME).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists()) file.delete()
                tmp.renameTo(file)
                prefs.edit().putLong("stamp", stamp).apply()
            }
            val opened = SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            db = opened
            return opened
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
        val out = ArrayList<FoodSummary>()
        open().rawQuery(sql, arrayOf(match, tokens.first() + "%")).use { c ->
            while (c.moveToNext()) {
                out += FoodSummary(
                    source = FoodSource.USDA,
                    id = c.getLong(0),
                    name = c.getString(1),
                    detail = detail(c.getString(2), c.getInt(3)),
                    kcalPer100 = if (c.isNull(4)) null else c.getDouble(4),
                )
            }
        }
        return out
    }

    data class Candidate(val id: Long, val description: String, val source: Int, val portions: List<Portion>)

    /** Search results with their portions, used to auto-match recipe ingredients. */
    fun candidates(query: String, limit: Int = 60): List<Candidate> {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val sql = """
            SELECT f.id, f.description, f.source, f.portions
            FROM food_fts JOIN foods f ON f.id = food_fts.docid
            WHERE food_fts MATCH ?
            ORDER BY (f.description LIKE ?) DESC, length(f.description) ASC, f.n_count DESC
            LIMIT $limit
        """.trimIndent()
        val out = ArrayList<Candidate>()
        open().rawQuery(sql, arrayOf(tokens.joinToString(" ") { "$it*" }, tokens.first() + "%")).use { c ->
            while (c.moveToNext()) {
                out += Candidate(c.getLong(0), c.getString(1), c.getInt(2), parsePortions(c.getString(3)))
            }
        }
        return out
    }

    private fun parsePortions(json: String?): List<Portion> {
        val portions = ArrayList<Portion>()
        runCatching {
            val arr = JSONArray(json ?: "[]")
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                val grams = p.getDouble(1)
                if (grams > 0) portions += Portion(p.getString(0), grams)
            }
        }
        return portions
    }

    fun food(id: Long): Food? {
        return open().rawQuery(
            "SELECT description, category, source, nutrients, portions FROM foods WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            val portions = ArrayList<Portion>()
            runCatching {
                val arr = JSONArray(c.getString(4))
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONArray(i)
                    val grams = p.getDouble(1)
                    if (grams > 0) portions += Portion(p.getString(0), grams)
                }
            }
            Food(
                source = FoodSource.USDA,
                id = id,
                name = c.getString(0),
                detail = detail(c.getString(1), c.getInt(2)),
                per100g = NutrientCodec.decode(c.getString(3)),
                portions = portions,
            )
        }
    }

    fun foodCount(): Int = runCatching {
        open().rawQuery("SELECT COUNT(*) FROM foods", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }.getOrDefault(0)

    private fun detail(category: String?, source: Int): String {
        val src = when (source) {
            0 -> "USDA SR Legacy"
            1 -> "USDA Foundation"
            2 -> "USDA Survey (FNDDS)"
            else -> "USDA"
        }
        return if (category.isNullOrBlank()) src else "$category · $src"
    }

    companion object {
        const val DB_NAME = "usda.db"
        private val STOP_WORDS = setOf("and", "with", "of", "the", "a", "in", "or", "w")
    }
}
