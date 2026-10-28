package com.moblized.nutritrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val settings = Settings(profile = Profile(sex = Sex.MALE, age = 35, heightCm = 180.0, weightKg = 84.0), profileSet = true)
    private val targets = Targets.compute(settings)

    private fun print(title: String, list: List<Suggestion>) {
        println("SUGGEST == $title")
        list.forEach { s ->
            val gains = s.gains.take(3).joinToString { "${Nutrients.byId[it.first]?.name} +${it.second}%" }
            println("SUGGEST ${s.candidate.group.key} | ${s.candidate.name} | ${s.candidate.unitLabel} | ${s.candidate.kcal.toInt()} kcal | $gains")
        }
    }

    private fun lightDay(): Map<Int, Double> = sumNutrients(
        listOf(
            TestFoods.entry(0, "Egg, whole, raw, fresh", 2.0, "large", 50.0, "2026-10-28", 1),
            TestFoods.entry(0, "Bananas, raw", 1.0, "medium", 118.0, "2026-10-28", 2),
            TestFoods.entry(1, "Chicken, breast, boneless, skinless, raw", 150.0, "g", 1.0, "2026-10-28", 3),
        ).map { NutrientCodec.decode(it.nutrients) },
    )

    @Test
    fun poolHasEverydayFoodsInEveryGroup() {
        val pool = TestFoods.pool()
        println("SUGGEST pool size ${pool.size}: " + pool.groupingBy { it.group }.eachCount())
        assertTrue("pool too small: ${pool.size}", pool.size >= 400)
        FoodGroup.entries.filter { it != FoodGroup.MINE }.forEach { g ->
            assertTrue("no ${g.label} in pool", pool.any { it.group == g })
        }
        assertTrue(pool.all { it.grams in 15.0..400.0 })
    }

    @Test
    fun ranksVariedFoodsForTheDaysGaps() {
        val pool = TestFoods.pool()
        val totals = lightDay()
        val list = Suggestions.rank(pool, totals, targets)
        print("light day, best overall", list)
        assertTrue(list.size >= 10)
        assertTrue("more than 3 from one group", list.groupingBy { it.candidate.group }.eachCount().values.all { it <= 3 })
        assertEquals(list.size, list.map { Suggestions.family(it.candidate.name) }.toSet().size)
        val remaining = Suggestions.remainingKcal(totals, targets)
        assertTrue(list.all { it.candidate.kcal <= remaining * 1.1 })
    }

    @Test
    fun focusesOnOneNutrient() {
        val pool = TestFoods.pool()
        val list = Suggestions.rank(pool, lightDay(), targets, focus = Nutrients.VIT_D, limit = 8)
        print("focus vitamin D", list)
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { s -> s.gains.any { it.first == Nutrients.VIT_D && it.second >= 8 } })
    }

    @Test
    fun suggestsLightFoodsNearTheCalorieTarget() {
        val pool = TestFoods.pool()
        val totals = lightDay().toMutableMap()
        totals[Nutrients.ENERGY] = (targets[Nutrients.ENERGY] ?: 2000.0) - 80
        val list = Suggestions.rank(pool, totals, targets, limit = 8)
        print("80 kcal left", list)
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.candidate.kcal <= Suggestions.LIGHT_KCAL })
    }

    @Test
    fun startsTheDayWithNutrientPackedFoods() {
        val list = Suggestions.rank(TestFoods.pool(), emptyMap(), targets)
        print("nothing logged", list)
        assertTrue(list.size >= 10)
    }

    @Test
    fun groupsFamiliesAndCleansNames() {
        assertEquals(Suggestions.family("Fish, trout, steamed"), Suggestions.family("Fish, trout, baked or broiled"))
        assertTrue(Suggestions.family("Fish, trout, steamed") != Suggestions.family("Fish, salmon, baked or broiled"))
        assertEquals(Suggestions.family("Milk, fat free (skim)"), Suggestions.family("Milk, lactose free, fat free (skim)"))
        assertEquals(
            "Cheese, cheddar",
            Suggestions.displayName("Cheese, cheddar (Includes foods for USDA's Food Distribution Program)"),
        )
    }
}
