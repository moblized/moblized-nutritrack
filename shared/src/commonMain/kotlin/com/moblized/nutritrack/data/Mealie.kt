package com.moblized.nutritrack.data

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    private suspend fun call(path: String, method: String = "GET", body: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            try {
                val r = http.request(base + path) {
                    this.method = HttpMethod.parse(method)
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                    if (method != "GET") {
                        contentType(ContentType.Application.Json)
                        setBody((body ?: JsonObject(emptyMap())).toString())
                    }
                }
                val text = r.bodyAsText()
                if (r.status.value !in 200..299) {
                    val msg = when (r.status.value) {
                        401, 403 -> "Mealie rejected the API token"
                        404 -> "Not found on this Mealie server"
                        else -> "Mealie returned HTTP ${r.status.value}"
                    }
                    throw MealieException(msg, r.status.value)
                }
                text
            } catch (e: MealieException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = (e.message.orEmpty() + " " + e::class.simpleName.orEmpty()).lowercase()
                throw when {
                    "ssl" in m || "tls" in m || "certificate" in m || "handshake" in m ->
                        MealieException("Secure connection to $base failed (${e.message}). Try the http:// address.")
                    e is IllegalArgumentException || "urlparser" in m || "illegal" in m ->
                        MealieException("That doesn't look like a valid server address")
                    else -> MealieException("Can't reach $base. Check the address and that your phone is on your home network.")
                }
            }
        }

    /** Returns the signed-in user's name; used to test the connection. */
    suspend fun whoAmI(): String {
        val o = parseObject(call("/api/users/self")) ?: return ""
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
        val o = parseObject(call("/api/recipes/" + enc(slug))) ?: throw MealieException("Mealie sent a recipe this app can't read")
        val ingredients = ArrayList<MealieIngredient>()
        for (ing in o.arr("recipeIngredient")?.objects().orEmpty()) {
            val text = ing.str("originalText").ifBlank { ing.str("display") }.ifBlank { ing.str("note") }.trim()
            val qty = ing.dbl("quantity")?.takeIf { it.isFinite() && it > 0 }
            val unit = ing.obj("unit")?.let { u -> u.str("name").ifBlank { u.str("abbreviation") } }
                ?.takeIf { it.isNotBlank() }
            val food = ing.obj("food")?.str("name")?.takeIf { it.isNotBlank() }
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
        val servings = o.dbl("recipeServings")?.takeIf { it.isFinite() && it > 0 }
            ?: o.dbl("recipeYieldQuantity")?.takeIf { it.isFinite() && it > 0 }
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
            val r = e.obj("recipe")
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
            if (decimals == 0) it.roundToInt().toString() else formatFixed(it, 1)
        }
        val unsat = listOfNotNull(perServing[Nutrients.MUFA], perServing[Nutrients.PUFA]).takeIf { it.isNotEmpty() }?.sum()
        val nutrition = buildJsonObject {
            v(Nutrients.ENERGY, 0)?.let { put("calories", it) }
            v(Nutrients.PROTEIN)?.let { put("proteinContent", it) }
            v(Nutrients.FAT)?.let { put("fatContent", it) }
            v(Nutrients.SAT_FAT)?.let { put("saturatedFatContent", it) }
            v(Nutrients.TRANS_FAT)?.let { put("transFatContent", it) }
            unsat?.let { put("unsaturatedFatContent", formatFixed(it, 1)) }
            v(Nutrients.CARBS)?.let { put("carbohydrateContent", it) }
            v(Nutrients.FIBER)?.let { put("fiberContent", it) }
            v(Nutrients.SUGARS)?.let { put("sugarContent", it) }
            v(Nutrients.SODIUM, 0)?.let { put("sodiumContent", it) }
            v(Nutrients.CHOLESTEROL, 0)?.let { put("cholesterolContent", it) }
        }
        call("/api/recipes/" + enc(slug), method = "PATCH", body = JsonObject(mapOf("nutrition" to nutrition)))
    }

    /** Accepts both a bare JSON array and a paginated `{ "items": [...] }` response. */
    private fun items(text: String): List<JsonObject> {
        val el = parseJson(text.trim())
        val arr = (el as? JsonArray) ?: (el as? JsonObject)?.arr("items") ?: JsonArray(emptyList())
        return arr.objects()
    }

    private fun enc(s: String) = urlEncode(s)

}

private fun fmtNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
