package com.moblized.nutritrack.data

enum class FoodSource(val key: String) {
    USDA("usda"), CUSTOM("custom"), RECIPE("recipe");

    companion object {
        fun of(key: String): FoodSource = entries.firstOrNull { it.key == key } ?: USDA
    }
}

data class Portion(val label: String, val grams: Double)

data class Food(
    val source: FoodSource,
    val id: Long,
    val name: String,
    val detail: String,
    val per100g: Map<Int, Double>,
    val portions: List<Portion>,
) {
    /** Every unit the food can be measured in: its portions, then grams and ounces. */
    val units: List<Portion>
        get() = portions + listOf(Portion("g", 1.0), Portion("oz", OZ))

    fun defaultUnit(): Portion = portions.firstOrNull() ?: Portion("g", 1.0)

    fun defaultQuantity(): Double = if (portions.isEmpty()) 100.0 else 1.0

    companion object {
        const val OZ = 28.349523125
    }
}

data class FoodSummary(
    val source: FoodSource,
    val id: Long,
    val name: String,
    val detail: String,
    val kcalPer100: Double?,
)

object Meals {
    val names = listOf("Breakfast", "Lunch", "Dinner", "Snacks")
    fun name(i: Int) = names.getOrElse(i) { "Snacks" }

    /** A sensible default meal for the current time of day. */
    fun forHour(hour: Int): Int = when (hour) {
        in 4..10 -> 0
        in 11..15 -> 1
        in 16..21 -> 2
        else -> 3
    }
}
