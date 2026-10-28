package com.moblized.nutritrack.data

import com.moblized.nutritrack.data.Nutrients as N
import kotlin.math.roundToInt

/**
 * Daily targets from the US Dietary Reference Intakes (RDA/AI), adjusted for sex and age,
 * plus calorie and macro targets from the profile. User overrides win.
 */
object Targets {

    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun autoCalories(p: Profile): Int =
        ((bmr(p) * p.activity.factor + p.goal.kcalDelta) / 10.0).roundToInt() * 10

    /** Below this many kcal a day it's hard to meet nutrient needs without medical supervision. */
    fun calorieFloor(p: Profile): Int = if (p.sex == Sex.MALE) 1500 else 1200

    fun calories(s: Settings): Double = (s.calorieOverride ?: autoCalories(s.profile)).toDouble()

    /** Default targets before user overrides. */
    fun defaults(s: Settings): Map<Int, Double> {
        val p = s.profile
        val male = p.sex == Sex.MALE
        val age = p.age
        val kcal = calories(s)
        val kg = p.weightKg
        val t = HashMap<Int, Double>()

        t[N.ENERGY] = kcal
        t[N.PROTEIN] = kcal * s.proteinPct / 100.0 / 4.0
        t[N.CARBS] = kcal * s.carbPct / 100.0 / 4.0
        t[N.FAT] = kcal * s.fatPct / 100.0 / 9.0

        t[N.FIBER] = if (male) (if (age > 50) 30.0 else 38.0) else (if (age > 50) 21.0 else 25.0)
        t[N.ADDED_SUGARS] = kcal * 0.10 / 4.0
        t[N.SAT_FAT] = kcal * 0.10 / 9.0
        t[N.TRANS_FAT] = 2.0
        t[N.CHOLESTEROL] = 300.0
        t[N.CAFFEINE] = 400.0
        t[N.ALA] = if (male) 1.6 else 1.1
        t[N.LA] = if (male) (if (age > 50) 14.0 else 17.0) else (if (age > 50) 11.0 else 12.0)

        // Essential amino acids, RDA in mg per kg body weight (IOM 2005)
        t[N.HISTIDINE] = 14 * kg / 1000
        t[N.ISOLEUCINE] = 19 * kg / 1000
        t[N.LEUCINE] = 42 * kg / 1000
        t[N.LYSINE] = 38 * kg / 1000
        t[N.METHIONINE] = 19 * kg / 1000   // methionine + cysteine
        t[N.PHENYLALANINE] = 33 * kg / 1000 // phenylalanine + tyrosine
        t[N.THREONINE] = 20 * kg / 1000
        t[N.TRYPTOPHAN] = 5 * kg / 1000
        t[N.VALINE] = 24 * kg / 1000

        t[N.VIT_A] = if (male) 900.0 else 700.0
        t[N.VIT_C] = if (male) 90.0 else 75.0
        t[N.VIT_D] = if (age > 70) 20.0 else 15.0
        t[N.VIT_E] = 15.0
        t[N.VIT_K] = if (male) 120.0 else 90.0
        t[N.THIAMIN] = if (male) 1.2 else 1.1
        t[N.RIBOFLAVIN] = if (male) 1.3 else 1.1
        t[N.NIACIN] = if (male) 16.0 else 14.0
        t[N.PANTOTHENIC] = 5.0
        t[N.VIT_B6] = if (age > 50) (if (male) 1.7 else 1.5) else 1.3
        t[N.BIOTIN] = 30.0
        t[N.FOLATE] = 400.0
        t[N.VIT_B12] = 2.4
        t[N.CHOLINE] = if (male) 550.0 else 425.0

        t[N.CALCIUM] = when {
            age > 70 -> 1200.0
            !male && age > 50 -> 1200.0
            else -> 1000.0
        }
        t[N.COPPER] = 0.9
        t[N.IODINE] = 150.0
        t[N.IRON] = if (male || age > 50) 8.0 else 18.0
        t[N.MAGNESIUM] = if (male) (if (age > 30) 420.0 else 400.0) else (if (age > 30) 320.0 else 310.0)
        t[N.MANGANESE] = if (male) 2.3 else 1.8
        t[N.PHOSPHORUS] = 700.0
        t[N.POTASSIUM] = if (male) 3400.0 else 2600.0
        t[N.SELENIUM] = 55.0
        t[N.SODIUM] = 2300.0
        t[N.ZINC] = if (male) 11.0 else 8.0
        return t
    }

    fun compute(s: Settings): Map<Int, Double> {
        val t = defaults(s).toMutableMap()
        for ((id, v) in s.overrides) if (v > 0) t[id] = v
        return t
    }

    /** Tolerable upper intake levels (from all food sources). */
    fun upperLimits(s: Settings): Map<Int, Double> {
        val age = s.profile.age
        return mapOf(
            N.VIT_C to 2000.0,
            N.VIT_D to 100.0,
            N.VIT_B6 to 100.0,
            N.CHOLINE to 3500.0,
            N.CALCIUM to if (age > 50) 2000.0 else 2500.0,
            N.COPPER to 10.0,
            N.IODINE to 1100.0,
            N.IRON to 45.0,
            N.MANGANESE to 11.0,
            N.PHOSPHORUS to if (age > 70) 3000.0 else 4000.0,
            N.SELENIUM to 400.0,
            N.ZINC to 40.0,
        )
    }
}
