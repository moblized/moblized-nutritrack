package com.moblized.nutritrack.ui

import com.moblized.nutritrack.data.formatFixed
import com.moblized.nutritrack.data.formatGrouped
import com.moblized.nutritrack.data.minusDays
import com.moblized.nutritrack.data.now
import com.moblized.nutritrack.data.plusDays
import com.moblized.nutritrack.data.shortLabel
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formats a nutrient amount with precision that suits its size. */
fun fmtAmount(v: Double): String {
    val a = abs(v)
    return when {
        a == 0.0 -> "0"
        a >= 100 -> formatGrouped(v.roundToInt())
        a >= 10 -> formatFixed(v, 1).trimZero()
        a >= 1 -> formatFixed(v, 1).trimZero()
        a >= 0.1 -> formatFixed(v, 2).trimZero()
        else -> formatFixed(v, 3).trimZero()
    }
}

fun fmtKcal(v: Double): String = formatGrouped(v.roundToInt())

fun fmtQty(v: Double): String =
    if (abs(v - v.roundToInt()) < 1e-9) v.roundToInt().toString()
    else formatFixed(v, 2).trimZero()

private fun String.trimZero(): String =
    if (contains('.')) trimEnd('0').trimEnd('.') else this

fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun dateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> date.shortLabel(today)
}

fun pct(value: Double, target: Double?): Int? =
    if (target == null || target <= 0) null else (value / target * 100).roundToInt()

/**
 * How much of something: "60 g", "½ tsp · 2.5 g", "2 large · 100 g".
 * USDA portion labels often start with "1" ("1 tsp", "1 cup, chopped"); that 1 is folded into the amount.
 */
fun amountText(quantity: Double, unitLabel: String, grams: Double): String {
    val label = unitLabel.trim()
    if (label == "g") return "${fmtAmount(grams)} g"
    val unit = when {
        label.startsWith("1 ") -> label.removePrefix("1 ")
        label.firstOrNull()?.isDigit() == true -> "× $label"
        else -> label
    }
    return "${fmtQty(quantity)} $unit · ${fmtAmount(grams)} g"
}
