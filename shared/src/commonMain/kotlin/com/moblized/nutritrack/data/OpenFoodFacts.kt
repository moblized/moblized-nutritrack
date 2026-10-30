package com.moblized.nutritrack.data

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Barcode lookups against the Open Food Facts database (ODbL). */
object OpenFoodFacts {

    /** OFF key -> (USDA nutrient id, factor converting OFF's grams to the app's unit). */
    private val MAP: List<Triple<String, Int, Double>> = listOf(
        Triple("proteins", Nutrients.PROTEIN, 1.0),
        Triple("carbohydrates", Nutrients.CARBS, 1.0),
        Triple("fat", Nutrients.FAT, 1.0),
        Triple("saturated-fat", Nutrients.SAT_FAT, 1.0),
        Triple("monounsaturated-fat", Nutrients.MUFA, 1.0),
        Triple("polyunsaturated-fat", Nutrients.PUFA, 1.0),
        Triple("trans-fat", Nutrients.TRANS_FAT, 1.0),
        Triple("cholesterol", Nutrients.CHOLESTEROL, 1000.0),
        Triple("sugars", Nutrients.SUGARS, 1.0),
        Triple("added-sugars", Nutrients.ADDED_SUGARS, 1.0),
        Triple("fiber", Nutrients.FIBER, 1.0),
        Triple("starch", Nutrients.STARCH, 1.0),
        Triple("sodium", Nutrients.SODIUM, 1000.0),
        Triple("potassium", Nutrients.POTASSIUM, 1000.0),
        Triple("calcium", Nutrients.CALCIUM, 1000.0),
        Triple("iron", Nutrients.IRON, 1000.0),
        Triple("magnesium", Nutrients.MAGNESIUM, 1000.0),
        Triple("zinc", Nutrients.ZINC, 1000.0),
        Triple("phosphorus", Nutrients.PHOSPHORUS, 1000.0),
        Triple("copper", Nutrients.COPPER, 1000.0),
        Triple("manganese", Nutrients.MANGANESE, 1000.0),
        Triple("selenium", Nutrients.SELENIUM, 1_000_000.0),
        Triple("iodine", Nutrients.IODINE, 1_000_000.0),
        Triple("vitamin-a", Nutrients.VIT_A, 1_000_000.0),
        Triple("vitamin-c", Nutrients.VIT_C, 1000.0),
        Triple("vitamin-d", Nutrients.VIT_D, 1_000_000.0),
        Triple("vitamin-e", Nutrients.VIT_E, 1000.0),
        Triple("vitamin-k", Nutrients.VIT_K, 1_000_000.0),
        Triple("vitamin-b1", Nutrients.THIAMIN, 1000.0),
        Triple("vitamin-b2", Nutrients.RIBOFLAVIN, 1000.0),
        Triple("vitamin-pp", Nutrients.NIACIN, 1000.0),
        Triple("pantothenic-acid", Nutrients.PANTOTHENIC, 1000.0),
        Triple("vitamin-b6", Nutrients.VIT_B6, 1000.0),
        Triple("biotin", Nutrients.BIOTIN, 1_000_000.0),
        Triple("vitamin-b9", Nutrients.FOLATE, 1_000_000.0),
        Triple("vitamin-b12", Nutrients.VIT_B12, 1_000_000.0),
        Triple("caffeine", Nutrients.CAFFEINE, 1000.0),
    )

    sealed interface Result {
        data class Found(val food: CustomFood) : Result
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    suspend fun lookup(barcode: String): Result = withContext(Dispatchers.IO) {
        val codes = buildList {
            add(barcode)
            if (barcode.length == 12) add("0$barcode")
        }
        var lastError: String? = null
        for (code in codes) {
            try {
                val food = fetch(code, barcode)
                if (food != null) return@withContext Result.Found(food)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e::class.simpleName ?: "error"
            }
        }
        if (lastError != null) Result.Error(lastError) else Result.NotFound
    }

    private suspend fun fetch(code: String, originalBarcode: String): CustomFood? {
        val r = http.get(
            "https://world.openfoodfacts.org/api/v2/product/$code.json" +
                "?fields=product_name,product_name_en,brands,nutriments,serving_size,serving_quantity",
        ) {
            header("User-Agent", "NutriTrack/1.0 (github.com/moblized/moblized-nutritrack)")
        }
        val status = r.status.value
        if (status == 404) return null
        if (status != 200) throw IllegalStateException("Open Food Facts returned HTTP $status")
        val json = parseObject(r.bodyAsText()) ?: return null
        if ((json.long("status") ?: 0L) != 1L) return null
        val p = json.obj("product") ?: return null
        val n = p.obj("nutriments") ?: JsonObject(emptyMap())

        val per100 = HashMap<Int, Double>()
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 }
        if (kcal != null) per100[Nutrients.ENERGY] = kcal
        for ((key, id, factor) in MAP) {
            n.num("${key}_100g")?.let { per100[id] = it * factor }
        }
        if (Nutrients.SODIUM !in per100) {
            n.num("salt_100g")?.let { per100[Nutrients.SODIUM] = it / 2.5 * 1000.0 }
        }

        val name = p.str("product_name_en").ifBlank { p.str("product_name") }
            .ifBlank { "Product $originalBarcode" }
        val brand = p.str("brands").split(',').firstOrNull()?.trim().orEmpty()
        val servingGrams = p.dbl("serving_quantity") ?: Double.NaN
        val servingSize = p.str("serving_size")
        val hasServing = servingGrams.isFinite() && servingGrams > 0
        return CustomFood(
            name = name.trim(),
            brand = brand,
            barcode = originalBarcode,
            servingLabel = if (hasServing) servingSize.ifBlank { "1 serving" } else "100 g",
            servingGrams = if (hasServing) servingGrams else 100.0,
            per100 = NutrientCodec.encode(per100),
        )
    }

    private fun JsonObject.num(key: String): Double? {
        val v = dbl(key) ?: return null
        return if (v.isFinite() && v >= 0) v else null
    }
}
