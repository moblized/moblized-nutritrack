package com.moblized.nutritrack.data

enum class NutrientGroup(val title: String) {
    GENERAL("General"),
    CARBS("Carbohydrates"),
    LIPIDS("Lipids"),
    PROTEIN("Protein & amino acids"),
    VITAMINS("Vitamins"),
    MINERALS("Minerals"),
}

/** GOAL = reach at least the target, LIMIT = stay under it, NONE = informational. */
enum class TargetKind { GOAL, LIMIT, NONE }

data class NutrientDef(
    val id: Int,
    val name: String,
    val unit: String,
    val group: NutrientGroup,
    val kind: TargetKind = TargetKind.GOAL,
)

/** Nutrient ids are USDA FoodData Central nutrient ids; amounts are stored per 100 g. */
object Nutrients {
    const val ENERGY = 1008
    const val ALCOHOL = 1018
    const val CAFFEINE = 1057
    const val WATER = 1051

    const val CARBS = 1005
    const val FIBER = 1079
    const val SUGARS = 2000
    const val ADDED_SUGARS = 1235
    const val STARCH = 1009

    const val FAT = 1004
    const val SAT_FAT = 1258
    const val MUFA = 1292
    const val PUFA = 1293
    const val TRANS_FAT = 1257
    const val CHOLESTEROL = 1253
    const val ALA = 1404
    const val EPA = 1278
    const val DHA = 1272
    const val DPA = 1280
    const val LA = 1269

    const val PROTEIN = 1003
    const val TRYPTOPHAN = 1210
    const val THREONINE = 1211
    const val ISOLEUCINE = 1212
    const val LEUCINE = 1213
    const val LYSINE = 1214
    const val METHIONINE = 1215
    const val CYSTINE = 1216
    const val PHENYLALANINE = 1217
    const val TYROSINE = 1218
    const val VALINE = 1219
    const val ARGININE = 1220
    const val HISTIDINE = 1221
    const val ALANINE = 1222
    const val ASPARTIC = 1223
    const val GLUTAMIC = 1224
    const val GLYCINE = 1225
    const val PROLINE = 1226
    const val SERINE = 1227

    const val VIT_A = 1106
    const val BETA_CAROTENE = 1107
    const val LUTEIN = 1123
    const val LYCOPENE = 1122
    const val VIT_C = 1162
    const val VIT_D = 1114
    const val VIT_E = 1109
    const val VIT_K = 1185
    const val THIAMIN = 1165
    const val RIBOFLAVIN = 1166
    const val NIACIN = 1167
    const val PANTOTHENIC = 1170
    const val VIT_B6 = 1175
    const val BIOTIN = 1176
    const val FOLATE = 1190
    const val VIT_B12 = 1178
    const val CHOLINE = 1180

    const val CALCIUM = 1087
    const val COPPER = 1098
    const val IRON = 1089
    const val MAGNESIUM = 1090
    const val MANGANESE = 1101
    const val PHOSPHORUS = 1091
    const val POTASSIUM = 1092
    const val SELENIUM = 1103
    const val SODIUM = 1093
    const val ZINC = 1095
    const val IODINE = 1100
    const val FLUORIDE = 1099

    private val G = NutrientGroup.GENERAL
    private val C = NutrientGroup.CARBS
    private val L = NutrientGroup.LIPIDS
    private val P = NutrientGroup.PROTEIN
    private val V = NutrientGroup.VITAMINS
    private val M = NutrientGroup.MINERALS
    private val LIMIT = TargetKind.LIMIT
    private val NONE = TargetKind.NONE

    val all: List<NutrientDef> = listOf(
        NutrientDef(ENERGY, "Energy", "kcal", G),
        NutrientDef(ALCOHOL, "Alcohol", "g", G, NONE),
        NutrientDef(CAFFEINE, "Caffeine", "mg", G, LIMIT),
        NutrientDef(WATER, "Water (from food)", "g", G, NONE),

        NutrientDef(CARBS, "Carbs", "g", C),
        NutrientDef(FIBER, "Fiber", "g", C),
        NutrientDef(STARCH, "Starch", "g", C, NONE),
        NutrientDef(SUGARS, "Sugars", "g", C, NONE),
        NutrientDef(ADDED_SUGARS, "Added sugars", "g", C, LIMIT),

        NutrientDef(FAT, "Fat", "g", L),
        NutrientDef(SAT_FAT, "Saturated", "g", L, LIMIT),
        NutrientDef(MUFA, "Monounsaturated", "g", L, NONE),
        NutrientDef(PUFA, "Polyunsaturated", "g", L, NONE),
        NutrientDef(ALA, "Omega-3 ALA", "g", L),
        NutrientDef(EPA, "Omega-3 EPA", "g", L, NONE),
        NutrientDef(DHA, "Omega-3 DHA", "g", L, NONE),
        NutrientDef(DPA, "Omega-3 DPA", "g", L, NONE),
        NutrientDef(LA, "Omega-6 linoleic", "g", L),
        NutrientDef(TRANS_FAT, "Trans fat", "g", L, LIMIT),
        NutrientDef(CHOLESTEROL, "Cholesterol", "mg", L, LIMIT),

        NutrientDef(PROTEIN, "Protein", "g", P),
        NutrientDef(HISTIDINE, "Histidine", "g", P),
        NutrientDef(ISOLEUCINE, "Isoleucine", "g", P),
        NutrientDef(LEUCINE, "Leucine", "g", P),
        NutrientDef(LYSINE, "Lysine", "g", P),
        NutrientDef(METHIONINE, "Methionine", "g", P),
        NutrientDef(CYSTINE, "Cystine", "g", P, NONE),
        NutrientDef(PHENYLALANINE, "Phenylalanine", "g", P),
        NutrientDef(TYROSINE, "Tyrosine", "g", P, NONE),
        NutrientDef(THREONINE, "Threonine", "g", P),
        NutrientDef(TRYPTOPHAN, "Tryptophan", "g", P),
        NutrientDef(VALINE, "Valine", "g", P),
        NutrientDef(ARGININE, "Arginine", "g", P, NONE),
        NutrientDef(ALANINE, "Alanine", "g", P, NONE),
        NutrientDef(ASPARTIC, "Aspartic acid", "g", P, NONE),
        NutrientDef(GLUTAMIC, "Glutamic acid", "g", P, NONE),
        NutrientDef(GLYCINE, "Glycine", "g", P, NONE),
        NutrientDef(PROLINE, "Proline", "g", P, NONE),
        NutrientDef(SERINE, "Serine", "g", P, NONE),

        NutrientDef(VIT_A, "Vitamin A (RAE)", "µg", V),
        NutrientDef(BETA_CAROTENE, "Beta-carotene", "µg", V, NONE),
        NutrientDef(LUTEIN, "Lutein + zeaxanthin", "µg", V, NONE),
        NutrientDef(LYCOPENE, "Lycopene", "µg", V, NONE),
        NutrientDef(VIT_C, "Vitamin C", "mg", V),
        NutrientDef(VIT_D, "Vitamin D", "µg", V),
        NutrientDef(VIT_E, "Vitamin E", "mg", V),
        NutrientDef(VIT_K, "Vitamin K", "µg", V),
        NutrientDef(THIAMIN, "B1 Thiamin", "mg", V),
        NutrientDef(RIBOFLAVIN, "B2 Riboflavin", "mg", V),
        NutrientDef(NIACIN, "B3 Niacin", "mg", V),
        NutrientDef(PANTOTHENIC, "B5 Pantothenic acid", "mg", V),
        NutrientDef(VIT_B6, "B6 Pyridoxine", "mg", V),
        NutrientDef(BIOTIN, "B7 Biotin", "µg", V),
        NutrientDef(FOLATE, "B9 Folate (DFE)", "µg", V),
        NutrientDef(VIT_B12, "B12 Cobalamin", "µg", V),
        NutrientDef(CHOLINE, "Choline", "mg", V),

        NutrientDef(CALCIUM, "Calcium", "mg", M),
        NutrientDef(COPPER, "Copper", "mg", M),
        NutrientDef(IODINE, "Iodine", "µg", M),
        NutrientDef(IRON, "Iron", "mg", M),
        NutrientDef(MAGNESIUM, "Magnesium", "mg", M),
        NutrientDef(MANGANESE, "Manganese", "mg", M),
        NutrientDef(PHOSPHORUS, "Phosphorus", "mg", M),
        NutrientDef(POTASSIUM, "Potassium", "mg", M),
        NutrientDef(SELENIUM, "Selenium", "µg", M),
        NutrientDef(SODIUM, "Sodium", "mg", M, LIMIT),
        NutrientDef(ZINC, "Zinc", "mg", M),
        NutrientDef(FLUORIDE, "Fluoride", "µg", M, NONE),
    )

    val byId: Map<Int, NutrientDef> = all.associateBy { it.id }

    /** Shown on the diary's highlight card. */
    val highlights = listOf(FIBER, VIT_D, VIT_C, VIT_B12, CALCIUM, IRON, MAGNESIUM, POTASSIUM)

    /** Shown on a nutrition-label style custom food form. */
    val labelFields = listOf(
        ENERGY, FAT, SAT_FAT, TRANS_FAT, CHOLESTEROL, SODIUM, CARBS, FIBER, SUGARS,
        ADDED_SUGARS, PROTEIN, VIT_D, CALCIUM, IRON, POTASSIUM,
    )

    /** The essentials: what a US Nutrition Facts label lists. All the app shows when detailed nutrition is off. */
    val essentials: Set<Int> = labelFields.toSet()

    /** Diary highlights when detailed nutrition is off. */
    private val essentialHighlights = listOf(FIBER, VIT_D, CALCIUM, IRON, POTASSIUM)

    /** Every nutrient (detailed) or just the label essentials, in display order. */
    fun shown(detailed: Boolean): List<NutrientDef> = if (detailed) all else all.filter { it.id in essentials }

    fun highlights(detailed: Boolean): List<Int> = if (detailed) highlights else essentialHighlights
}

object NutrientCodec {
    fun decode(s: String?): Map<Int, Double> {
        if (s.isNullOrBlank()) return emptyMap()
        val out = HashMap<Int, Double>()
        for (part in s.split(';')) {
            val i = part.indexOf(':')
            if (i <= 0) continue
            val id = part.substring(0, i).toIntOrNull() ?: continue
            val v = part.substring(i + 1).toDoubleOrNull() ?: continue
            out[id] = v
        }
        return out
    }

    fun encode(m: Map<Int, Double>): String =
        m.entries.filter { it.value.isFinite() }.sortedBy { it.key }
            .joinToString(";") { "${it.key}:${it.value}" }
}

fun Map<Int, Double>.scaled(factor: Double): Map<Int, Double> = mapValues { it.value * factor }

fun sumNutrients(maps: Iterable<Map<Int, Double>>): Map<Int, Double> {
    val out = HashMap<Int, Double>()
    for (m in maps) for ((k, v) in m) out[k] = (out[k] ?: 0.0) + v
    return out
}
