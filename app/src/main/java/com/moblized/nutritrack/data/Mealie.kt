package com.moblized.nutritrack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

class MealieException(message: String, val code: Int = 0) : Exception(message)

data class MealieRecipeSummary(val slug: String, val name: String, val description: String)

data class MealieIngredient(
    /** Parsed by Mealie's ingredient parser, when the recipe has been parsed. */
    val quantity: Double?,
    val unit: String?,
    val food: String?,
    /** The line as written in the recipe. */
    val text: String,
    /** Mealie's note for a parsed ingredient, e.g. "turkey" for pepperoni or "low-fat" for mozzarella. */
    val note: String? = null,
)

data class MealieRecipe(
    val slug: String,
    val name: String,
    val servings: Double?,
    val ingredients: List<MealieIngredient>,
)

data class MealiePlanEntry(
    val date: String,
    val entryType: String,
    val title: String,
    val recipeSlug: String?,
    val recipeName: String?,
) {
    /** NutriTrack meal index for the Mealie entry type. */
    val meal: Int
        get() = when (entryType.lowercase()) {
            "breakfast" -> 0
            "lunch" -> 1
            "dinner", "side" -> 2
            else -> 3
        }

    val displayName: String get() = recipeName?.takeIf { it.isNotBlank() } ?: title
}

/** Minimal client for a self-hosted Mealie server (v1 and v2 APIs). */
class MealieClient(baseUrl: String, private val token: String) {
    private val base: String = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(base + path)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                if (method == "GET") {
                    builder.get()
                } else {
                    builder.method(method, (body ?: JSONObject()).toString().toRequestBody(JSON))
                }
                http.newCall(builder.build()).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        val msg = when (r.code) {
                            401, 403 -> "Mealie rejected the API token"
                            404 -> "Not found on this Mealie server"
                            else -> "Mealie returned HTTP ${r.code}"
                        }
                        throw MealieException(msg, r.code)
                    }
                    text
                }
            } catch (e: SSLException) {
                throw MealieException("Secure connection to $base failed (${e.message}). Try the http:// address.")
            } catch (e: IOException) {
                throw MealieException("Can't reach $base. Check the address and that your phone is on your home network.")
            } catch (e: IllegalArgumentException) {
                throw MealieException("That doesn't look like a valid server address")
            }
        }

    /** Returns the signed-in user's name; used to test the connection. */
    suspend fun whoAmI(): String {
        val o = JSONObject(call("/api/users/self"))
        return o.str("fullName").ifBlank { o.str("username") }.ifBlank { o.str("email") }
    }

    suspend fun recipes(search: String, page: Int = 1): List<MealieRecipeSummary> {
        val q = buildString {
            append("/api/recipes?page=$page&perPage=50&orderBy=name&orderDirection=asc")
            if (search.isNotBlank()) append("&search=").append(enc(search.trim()))
        }
        return items(call(q)).map {
            MealieRecipeSummary(it.str("slug"), it.str("name"), it.str("description"))
        }.filter { it.slug.isNotBlank() }
    }

    suspend fun recipe(slug: String): MealieRecipe {
        val o = JSONObject(call("/api/recipes/" + enc(slug)))
        val ingredients = ArrayList<MealieIngredient>()
        val arr = o.optJSONArray("recipeIngredient") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val ing = arr.optJSONObject(i) ?: continue
            val text = ing.str("originalText").ifBlank { ing.str("display") }.ifBlank { ing.str("note") }.trim()
            val qty = ing.optDouble("quantity", Double.NaN).takeIf { it.isFinite() && it > 0 }
            val unit = ing.optJSONObject("unit")?.let { u -> u.str("name").ifBlank { u.str("abbreviation") } }
                ?.takeIf { it.isNotBlank() }
            val food = ing.optJSONObject("food")?.str("name")?.takeIf { it.isNotBlank() }
            if (text.isBlank() && food == null) continue
            val note = ing.str("note").takeIf { it.isNotBlank() && food != null }
            // Amount and unit are only meaningful once Mealie has parsed the line into a food;
            // unparsed lines keep a placeholder quantity, so read the amount from the text instead.
            ingredients += MealieIngredient(
                quantity = if (food != null) qty else null,
                unit = if (food != null) unit else null,
                food = food,
                text = text.ifBlank { listOfNotNull(qty?.let { fmtNumber(it) }, unit, food, note).joinToString(" ") },
                note = note,
            )
        }
        val servings = o.optDouble("recipeServings", Double.NaN).takeIf { it.isFinite() && it > 0 }
            ?: o.optDouble("recipeYieldQuantity", Double.NaN).takeIf { it.isFinite() && it > 0 }
            ?: Regex("""\d+(\.\d+)?""").find(o.str("recipeYield"))?.value?.toDoubleOrNull()?.takeIf { it > 0 }
        return MealieRecipe(o.str("slug").ifBlank { slug }, o.str("name"), servings, ingredients)
    }

    /** Meal plan entries for one day. Tries the v2 (households) API, then v1 (groups). */
    suspend fun mealPlan(date: LocalDate): List<MealiePlanEntry> {
        val query = "?start_date=$date&end_date=$date&page=1&perPage=100"
        val text = try {
            call("/api/households/mealplans$query")
        } catch (e: MealieException) {
            if (e.code == 404) call("/api/groups/mealplans$query") else throw e
        }
        return items(text).map { e ->
            val r = e.optJSONObject("recipe")
            MealiePlanEntry(
                date = e.str("date"),
                entryType = e.str("entryType").ifBlank { "dinner" },
                title = e.str("title").ifBlank { e.str("text") },
                recipeSlug = r?.str("slug")?.takeIf { it.isNotBlank() },
                recipeName = r?.str("name"),
            )
        }.filter { it.date.isBlank() || it.date == date.toString() }
    }

    /** Writes per-serving nutrition facts back to the Mealie recipe. */
    suspend fun saveNutrition(slug: String, perServing: Map<Int, Double>) {
        fun v(id: Int, decimals: Int = 1): String? = perServing[id]?.let {
            if (decimals == 0) it.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", it)
        }
        val unsat = listOfNotNull(perServing[Nutrients.MUFA], perServing[Nutrients.PUFA]).takeIf { it.isNotEmpty() }?.sum()
        val nutrition = JSONObject()
        v(Nutrients.ENERGY, 0)?.let { nutrition.put("calories", it) }
        v(Nutrients.PROTEIN)?.let { nutrition.put("proteinContent", it) }
        v(Nutrients.FAT)?.let { nutrition.put("fatContent", it) }
        v(Nutrients.SAT_FAT)?.let { nutrition.put("saturatedFatContent", it) }
        v(Nutrients.TRANS_FAT)?.let { nutrition.put("transFatContent", it) }
        unsat?.let { nutrition.put("unsaturatedFatContent", String.format(java.util.Locale.US, "%.1f", it)) }
        v(Nutrients.CARBS)?.let { nutrition.put("carbohydrateContent", it) }
        v(Nutrients.FIBER)?.let { nutrition.put("fiberContent", it) }
        v(Nutrients.SUGARS)?.let { nutrition.put("sugarContent", it) }
        v(Nutrients.SODIUM, 0)?.let { nutrition.put("sodiumContent", it) }
        v(Nutrients.CHOLESTEROL, 0)?.let { nutrition.put("cholesterolContent", it) }
        call("/api/recipes/" + enc(slug), method = "PATCH", body = JSONObject().put("nutrition", nutrition))
    }

    /** Accepts both a bare JSON array and a paginated `{ "items": [...] }` response. */
    private fun items(text: String): List<JSONObject> {
        val t = text.trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private fun fmtNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** org.json returns the text "null" for JSON nulls; this returns "" instead. */
internal fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key).trim()
