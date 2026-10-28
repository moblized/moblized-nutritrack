package com.moblized.nutritrack.data

import java.time.LocalDate
import java.time.LocalDateTime

/** A nutrient that is below its target: [percent] of the target reached. */
data class LowNutrient(val id: Int, val percent: Int) {
    val name: String get() = Nutrients.byId[id]?.name ?: ""
}

data class LowWarnings(
    /** Low for the day shown (only judged once the day is mostly over). */
    val today: List<LowNutrient>,
    /** Low on average over the last 7 logged days before the day shown. */
    val week: List<LowNutrient>,
    val weekDays: Int,
) {
    val isEmpty: Boolean get() = today.isEmpty() && week.isEmpty()

    /** Each nutrient once, worst first; the daily reading wins over the weekly one. */
    val all: List<LowNutrient>
        get() = (today + week.filter { w -> today.none { it.id == w.id } }).sortedBy { it.percent }
}

/**
 * Decides when to warn that something is low.
 *
 * - A single day is judged from [EVENING_HOUR] on (or any past day): under [DAY_THRESHOLD] of target.
 * - The week is the average of the last 7 days that have food logged: under [WEEK_THRESHOLD].
 *
 * Only nutrients that USDA data reports for most foods are checked. Nutrients like iodine or
 * biotin are missing for many foods and would look falsely low.
 */
object LowNutrients {
    const val EVENING_HOUR = 17
    const val DAY_THRESHOLD = 0.5
    const val WEEK_THRESHOLD = 0.7
    const val MIN_WEEK_DAYS = 3

    val watched: List<Int> = listOf(
        Nutrients.ENERGY, Nutrients.PROTEIN, Nutrients.FIBER,
        Nutrients.VIT_A, Nutrients.VIT_C, Nutrients.VIT_D, Nutrients.VIT_E, Nutrients.VIT_K,
        Nutrients.FOLATE, Nutrients.VIT_B12, Nutrients.CALCIUM, Nutrients.IRON,
        Nutrients.MAGNESIUM, Nutrients.POTASSIUM, Nutrients.ZINC,
    )

    fun dayIsJudged(date: LocalDate, now: LocalDateTime): Boolean =
        date.isBefore(now.toLocalDate()) || (date == now.toLocalDate() && now.hour >= EVENING_HOUR)

    fun below(totals: Map<Int, Double>, targets: Map<Int, Double>, threshold: Double): List<LowNutrient> =
        watched.mapNotNull { id ->
            val t = targets[id]?.takeIf { it > 0 } ?: return@mapNotNull null
            val frac = (totals[id] ?: 0.0) / t
            if (frac < threshold) LowNutrient(id, (frac * 100).toInt()) else null
        }.sortedBy { it.percent }

    /**
     * Warnings for [date]. [dayEntries] are that day's entries; [recentEntries] may cover any range
     * and are reduced to the 7 most recent logged days before [date].
     */
    fun warnings(
        date: LocalDate,
        now: LocalDateTime,
        dayEntries: List<DiaryEntry>,
        recentEntries: List<DiaryEntry>,
        targets: Map<Int, Double>,
    ): LowWarnings {
        val today = if (dayEntries.isNotEmpty() && dayIsJudged(date, now)) {
            below(sumNutrients(dayEntries.map { NutrientCodec.decode(it.nutrients) }), targets, DAY_THRESHOLD)
        } else {
            emptyList()
        }
        val byDay = recentEntries.filter { it.date < date.toString() }.groupBy { it.date }
        val days = byDay.keys.sortedDescending().take(7)
        val week = if (days.size >= MIN_WEEK_DAYS) {
            val sum = sumNutrients(days.flatMap { d -> byDay.getValue(d).map { NutrientCodec.decode(it.nutrients) } })
            below(sum.scaled(1.0 / days.size), targets, WEEK_THRESHOLD)
        } else {
            emptyList()
        }
        return LowWarnings(today, week, days.size)
    }

    /** Short text for a notification, e.g. "Vitamin D 13%, Fiber 21% and 2 more". */
    fun summary(list: List<LowNutrient>, max: Int = 3): String {
        val shown = list.take(max).joinToString(", ") { "${it.name} ${it.percent}%" }
        val more = list.size - max
        return if (more > 0) "$shown and $more more" else shown
    }
}
