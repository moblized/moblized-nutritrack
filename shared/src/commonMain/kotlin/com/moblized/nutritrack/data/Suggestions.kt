package com.moblized.nutritrack.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Food groups used to keep suggestions varied. "mine" is the person's own foods and recipes. */
enum class FoodGroup(val key: String, val label: String, val emoji: String) {
    VEG("veg", "Veggies", "🥦"),
    FRUIT("fruit", "Fruit", "🍓"),
    DAIRY("dairy", "Dairy & alternatives", "🥛"),
    PROTEIN("protein", "Meat, fish & eggs", "🍗"),
    LEGUMES("legumes", "Beans, nuts & seeds", "🥜"),
    GRAINS("grains", "Grains", "🌾"),
    MINE("mine", "Your foods", "⭐");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MINE
    }
}

/** Something that could be eaten: a food plus the amount to suggest, with nutrients for that amount. */
data class SuggestCandidate(
    val source: FoodSource,
    val id: Long,
    val name: String,
    val category: String?,
    val group: FoodGroup,
    val quantity: Double,
    val unitLabel: String,
    val unitGrams: Double,
    val nutrients: Map<Int, Double>,
) {
    val grams: Double get() = quantity * unitGrams
    val kcal: Double get() = nutrients[Nutrients.ENERGY] ?: 0.0
    val personal: Boolean get() = group == FoodGroup.MINE
}

/** A ranked suggestion and the share of each daily target it would add (only up to what's still needed). */
data class Suggestion(val candidate: SuggestCandidate, val score: Double, val gains: List<Pair<Int, Int>>)

/** Nutrients still needed today, as a fraction of the target. */
data class Gap(val id: Int, val fraction: Double) {
    val percentDone: Int get() = ((1 - fraction) * 100).roundToInt()
}

/**
 * Picks foods that fill today's nutrient gaps, per calorie, within the calories left.
 * A portion only gets credit up to what's still missing, so it favors foods that cover
 * several gaps over foods that pile more onto targets already met.
 */
object Suggestions {
    val goals: List<Int> = LowNutrients.watched - Nutrients.ENERGY

    /** Gaps worth filling: every watched nutrient, or only the label essentials when detail is off. */
    fun goals(detailed: Boolean): List<Int> = if (detailed) goals else goals.filter { it in Nutrients.essentials }
    private val limits = listOf(Nutrients.SODIUM, Nutrients.SAT_FAT, Nutrients.ADDED_SUGARS)

    /** Remaining kcal at or below which only light foods (up to [LIGHT_KCAL]) are suggested. */
    const val LIGHT_KCAL = 150.0

    fun gaps(totals: Map<Int, Double>, targets: Map<Int, Double>, detailed: Boolean = true): List<Gap> =
        goals(detailed).mapNotNull { id ->
            val t = targets[id]?.takeIf { it > 0 } ?: return@mapNotNull null
            val f = max(0.0, t - (totals[id] ?: 0.0)) / t
            if (f > 0.02) Gap(id, f) else null
        }.sortedByDescending { it.fraction }

    fun remainingKcal(totals: Map<Int, Double>, targets: Map<Int, Double>): Double =
        (targets[Nutrients.ENERGY] ?: 2000.0) - (totals[Nutrients.ENERGY] ?: 0.0)

    fun score(
        c: SuggestCandidate,
        totals: Map<Int, Double>,
        targets: Map<Int, Double>,
        focus: Int?,
        remaining: Double,
        detailed: Boolean = true,
    ): Suggestion? {
        val kcal = c.kcal
        if (remaining > LIGHT_KCAL) {
            if (kcal > remaining * 1.1) return null
        } else if (kcal > LIGHT_KCAL) {
            return null
        }
        var benefit = 0.0
        val gains = ArrayList<Pair<Int, Int>>()
        for (id in goals(detailed)) {
            val t = targets[id]?.takeIf { it > 0 } ?: continue
            val need = max(0.0, t - (totals[id] ?: 0.0))
            if (need <= 0) continue
            val fill = min(c.nutrients[id] ?: 0.0, need) / t
            if (fill <= 0) continue
            val gap = need / t
            val w = when {
                focus == null -> gap
                id == focus -> 2.5
                else -> 0.2 * gap
            }
            // Diminishing credit past half a day's worth, so one standout nutrient doesn't decide everything.
            val soft = min(fill, 0.5) + 0.3 * max(0.0, fill - 0.5)
            benefit += w * soft
            gains += id to (fill * 100).roundToInt()
        }
        if (focus != null && (gains.firstOrNull { it.first == focus }?.second ?: 0) < 8) return null
        var penalty = 0.0
        for (id in limits) {
            val t = targets[id]?.takeIf { it > 0 } ?: continue
            val food = c.nutrients[id] ?: 0.0
            val over = max(0.0, (totals[id] ?: 0.0) + food - t) / t
            penalty += 1.5 * over + 0.5 * max(0.0, food / t - 0.25)
        }
        val s = (benefit - penalty) / (0.6 + kcal / 250.0)
        if (s <= 0) return null
        return Suggestion(c, s, gains.filter { it.second > 0 }.sortedByDescending { it.second })
    }

    /** Best [limit] suggestions, at most one per kind of food and [groupCap] per food group. */
    fun rank(
        candidates: List<SuggestCandidate>,
        totals: Map<Int, Double>,
        targets: Map<Int, Double>,
        focus: Int? = null,
        limit: Int = 15,
        groupCap: Int = if (focus == null) 3 else 5,
        detailed: Boolean = true,
    ): List<Suggestion> {
        val remaining = remainingKcal(totals, targets)
        val scored = candidates.mapNotNull { score(it, totals, targets, focus, remaining, detailed) }.sortedByDescending { it.score }
        val families = HashSet<String>()
        val perGroup = HashMap<FoodGroup, Int>()
        val out = ArrayList<Suggestion>()
        for (s in scored) {
            val fam = family(s.candidate.name)
            val g = s.candidate.group
            if (fam in families) continue
            if (g != FoodGroup.MINE && (perGroup[g] ?: 0) >= groupCap) continue
            families += fam
            perGroup[g] = (perGroup[g] ?: 0) + 1
            out += s
            if (out.size >= limit) break
        }
        return out
    }

    private val cookingWords = Regex(
        "\\b(raw|cooked|baked|broiled|boiled|roasted|grilled|steamed|toasted|from fresh|from frozen|from canned|" +
            "from dried|no added fat|canned|frozen|fresh)\\b",
    )
    private val genericFirst = setOf("fish", "cheese", "cereal", "bread", "nuts", "seeds", "beans", "greens", "lettuce", "peppers", "squash", "juice")

    /** "Fish, trout, steamed" and "Fish, trout, baked" are the same kind of food; "Fish, salmon" is not. */
    fun family(name: String): String {
        val d = cookingWords.replace(name.lowercase(), "")
        val segs = d.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (segs.isEmpty()) return d
        return if (segs[0] in genericFirst && segs.size > 1) segs[0] + " " + segs[1].substringBefore(' ') else segs[0]
    }

    /** Drops USDA bookkeeping notes like "(Includes foods for USDA's Food Distribution Program)". */
    fun displayName(name: String): String = name.replace(Regex("\\s*\\((?:Includes|includes)[^)]*\\)"), "").trim()
}
