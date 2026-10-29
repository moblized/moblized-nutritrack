package com.moblized.nutritrack.data

import java.time.LocalDate

/** Small pure helpers for streaks and goal celebrations. */
object Progress {

    /** Consecutive days with something logged, ending today (or yesterday if today is still empty). */
    fun streak(loggedDates: Collection<String>, today: LocalDate): Int {
        val set = loggedDates.toHashSet()
        var d = if (today.toString() in set) today else today.minusDays(1)
        var n = 0
        while (d.toString() in set) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** Nutrients that can earn a celebration: protein plus the diary's key nutrients. */
    val celebrated: List<Int> = listOf(Nutrients.PROTEIN) + Nutrients.highlights

    fun celebrated(detailed: Boolean): List<Int> = listOf(Nutrients.PROTEIN) + Nutrients.highlights(detailed)

    fun goalsMet(totals: Map<Int, Double>, targets: Map<Int, Double>, detailed: Boolean = true): Set<Int> =
        celebrated(detailed).filter { id ->
            val t = targets[id]
            t != null && t > 0 && (totals[id] ?: 0.0) >= t
        }.toSet()

    /** Goals already reached per day, so a celebration only fires the moment a new goal is reached. */
    private val seen = HashMap<String, Set<Int>>()

    /** Returns goals that were not met the last time this day was shown; the first look sets the baseline. */
    @Synchronized
    fun newlyMet(date: String, met: Set<Int>): Set<Int> {
        val before = seen[date]
        seen[date] = met
        return if (before == null) emptySet() else met - before
    }

    fun celebrationMessage(newly: Set<Int>, allMet: Boolean): String {
        if (allMet) return "🌈 Every key nutrient hit today. Legendary!"
        val names = newly.mapNotNull { Nutrients.byId[it]?.name }
        return when (names.size) {
            0 -> ""
            1 -> "🎉 ${names[0]} goal reached!"
            2 -> "🎉 ${names[0]} and ${names[1]} goals reached!"
            else -> "🎉 ${names[0]} + ${names.size - 1} more goals reached!"
        }
    }
}
