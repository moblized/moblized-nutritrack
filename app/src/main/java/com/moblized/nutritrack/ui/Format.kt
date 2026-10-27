package com.moblized.nutritrack.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formats a nutrient amount with precision that suits its size. */
fun fmtAmount(v: Double): String {
    val a = abs(v)
    return when {
        a == 0.0 -> "0"
        a >= 100 -> String.format(Locale.US, "%,d", v.roundToInt())
        a >= 10 -> String.format(Locale.US, "%.1f", v).trimZero()
        a >= 1 -> String.format(Locale.US, "%.1f", v).trimZero()
        a >= 0.1 -> String.format(Locale.US, "%.2f", v).trimZero()
        else -> String.format(Locale.US, "%.3f", v).trimZero()
    }
}

fun fmtKcal(v: Double): String = String.format(Locale.US, "%,d", v.roundToInt())

fun fmtQty(v: Double): String =
    if (abs(v - v.roundToInt()) < 1e-9) v.roundToInt().toString()
    else String.format(Locale.US, "%.2f", v).trimZero()

private fun String.trimZero(): String =
    if (contains('.')) trimEnd('0').trimEnd('.') else this

fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun dateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy", Locale.getDefault()))
}

fun pct(value: Double, target: Double?): Int? =
    if (target == null || target <= 0) null else (value / target * 100).roundToInt()
