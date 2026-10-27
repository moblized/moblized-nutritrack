package com.moblized.nutritrack.data

import java.text.Normalizer

enum class UnitKind { MASS, VOLUME, COUNT }

data class ParsedIngredient(
    val quantity: Double?,
    /** Canonical unit (g, oz, cup, tbsp, clove, large, ...) or null for a bare count. */
    val unit: String?,
    val kind: UnitKind,
    /** Search terms for the food. */
    val query: String,
    /** Descriptive words (diced, boneless, ...) used to break ties between matches. */
    val hints: List<String>,
)

data class IngredientMatch(
    val item: RecipeItem?,
    /** The gram amount is a guess (no matching portion in the USDA data). */
    val estimated: Boolean,
    /** The recipe line has no amount ("salt to taste"). */
    val missingAmount: Boolean,
)

/** Parses recipe lines like "1 (14.5 oz) can diced tomatoes" or "2 ½ cups flour". */
object IngredientParser {
    val MASS = mapOf("g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "oz" to 28.349523, "lb" to 453.59237)
    val VOLUME = mapOf(
        "ml" to 1.0, "l" to 1000.0, "tsp" to 4.92892, "tbsp" to 14.7868, "cup" to 236.588,
        "fl oz" to 29.5735, "pint" to 473.176, "quart" to 946.353, "gallon" to 3785.41,
        "pinch" to 0.31, "dash" to 0.62,
    )

    val SYN: Map<String, String> = buildMap {
        listOf(
            "g" to "g gram grams gr", "kg" to "kg kgs kilogram kilograms", "mg" to "mg milligram milligrams",
            "oz" to "oz ounce ounces", "lb" to "lb lbs pound pounds",
            "ml" to "ml milliliter milliliters millilitre millilitres", "l" to "l liter liters litre litres",
            "tsp" to "tsp tsps teaspoon teaspoons", "tbsp" to "tbsp tbsps tbs tbl tablespoon tablespoons",
            "cup" to "cup cups c", "pint" to "pint pints pt", "quart" to "quart quarts qt",
            "gallon" to "gallon gallons gal", "pinch" to "pinch pinches", "dash" to "dash dashes",
            "clove" to "clove cloves", "slice" to "slice slices", "can" to "can cans",
            "piece" to "piece pieces pc pcs", "stick" to "stick sticks", "large" to "large lg",
            "medium" to "medium med", "small" to "small sm", "whole" to "whole", "bunch" to "bunch bunches",
            "head" to "head heads", "sprig" to "sprig sprigs", "handful" to "handful handfuls",
            "package" to "package packages pkg", "jar" to "jar jars", "stalk" to "stalk stalks",
            "leaf" to "leaf leaves",
        ).forEach { (canon, words) -> words.split(" ").forEach { put(it, canon) } }
    }

    val DESCRIPTORS: Set<String> = (
        "chopped diced minced sliced thinly finely roughly coarsely fresh freshly large medium small extra big " +
            "divided optional peeled seeded cored grated shredded melted softened cubed crushed packed heaping level " +
            "about approximately plus more taste to for serving garnish room temperature cold warm boneless skinless " +
            "trimmed rinsed drained halved quartered beaten lightly well firmly cut into pieces piece inch inches of " +
            "the a an or and your favorite good quality organic uncooked raw dry"
        ).split(" ").toSet()

    private val STOP = (
        "of the a an or and to for about approximately plus more taste your favorite good quality serving " +
            "garnish into cut inch inches piece pieces"
        ).split(" ").toSet()

    private val FRACTIONS = mapOf(
        '½' to "1/2", '¼' to "1/4", '¾' to "3/4", '⅓' to "1/3", '⅔' to "2/3",
        '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8", '⅕' to "1/5",
    )

    private const val NUM = """(\d+\s+\d+/\d+|\d+/\d+|\d*\.\d+|\d+)"""
    private val QTY = Regex("""^\s*$NUM(?:\s*(?:-|–|to)\s*$NUM)?""")
    private val PAREN_SIZE = Regex("""^\(\s*$NUM\s*-?\s*([a-zA-Z.]+(?:\s+oz)?)\s*\)\s*""")
    private val CAN_SIZE = Regex("""^\(\s*$NUM\s*-?\s*(oz|ounce|ounces|g|grams|ml)\.?\s*\)\s*""", RegexOption.IGNORE_CASE)
    private val CONTAINER = Regex("""^(can|cans|jar|jars|package|packages|bag|bags|box|boxes|carton|bottle)\b\.?\s*""", RegexOption.IGNORE_CASE)
    private val FLUID_OZ = Regex("""^(fl\.?\s*oz\.?|fluid\s+ounces?)\b\s*""", RegexOption.IGNORE_CASE)
    private val UNIT_WORD = Regex("""^([A-Za-z]+)\.?(\s+|$)""")
    private val WORD_SPLIT = Regex("[^a-z]+")

    fun num(s: String): Double? {
        val t = s.trim()
        if (t.contains(' ')) {
            val parts = t.split(Regex("\\s+"), limit = 2)
            val a = parts[0].toDoubleOrNull() ?: return null
            return a + (num(parts[1]) ?: 0.0)
        }
        if (t.contains('/')) {
            val (a, b) = t.split('/').let { (it.getOrNull(0)?.toDoubleOrNull() ?: return null) to (it.getOrNull(1)?.toDoubleOrNull() ?: return null) }
            return if (b == 0.0) null else a / b
        }
        return t.toDoubleOrNull()
    }

    fun normalize(text: String): String {
        var t = text
        for ((ch, frac) in FRACTIONS) {
            t = t.replace(Regex("(\\d)" + Regex.escape(ch.toString())), "$1 $frac").replace(ch.toString(), frac)
        }
        if (t.any { it.code > 127 }) {
            t = Normalizer.normalize(t, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").replace('⁄', '/')
        }
        return t
    }

    fun singular(w: String): String = when {
        w.length <= 3 -> w
        w.endsWith("oes") -> w.dropLast(2)
        w.endsWith("ies") -> w.dropLast(3) + "y"
        w.endsWith("ves") -> w.dropLast(3) + "f"
        w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    fun words(text: String): List<String> = text.lowercase().split(WORD_SPLIT).filter { it.isNotEmpty() }

    private fun hintsOf(text: String): List<String> =
        words(text).filter { it in DESCRIPTORS && it !in STOP }.map(::singular)

    private fun cleanQuery(text: String): String {
        var t = text.lowercase().replace(Regex("\\([^)]*\\)"), " ")
        t = t.split(Regex(",| - | – |;"), limit = 2).first()
        return words(t).filter { it !in DESCRIPTORS }.take(4).joinToString(" ") { singular(it) }
    }

    fun parse(text: String, quantityHint: Double? = null, unitHint: String? = null, foodHint: String? = null): ParsedIngredient {
        var rest = normalize(text).trim()
        var qty: Double? = null
        var unit: String? = null
        var canned = false

        QTY.find(rest)?.let { m ->
            val a = num(m.groupValues[1])
            val b = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { num(it) }
            qty = if (a != null && b != null) (a + b) / 2 else a
            rest = rest.substring(m.range.last + 1).trim()
        }
        PAREN_SIZE.find(rest)?.let { m ->
            val inner = SYN[m.groupValues[2].lowercase().trimEnd('.')]
            val n = num(m.groupValues[1])
            if (n != null && (inner in MASS || inner in VOLUME)) {
                qty = (qty ?: 1.0) * n
                unit = inner
                rest = rest.substring(m.range.last + 1)
                if (Regex("^(can|cans)\\b", RegexOption.IGNORE_CASE).containsMatchIn(rest)) canned = true
                rest = rest.replace(CONTAINER, "")
            }
        }
        if (unit == null) {
            val fl = FLUID_OZ.find(rest)
            if (fl != null) {
                unit = "fl oz"
                rest = rest.substring(fl.range.last + 1)
            } else {
                UNIT_WORD.find(rest)?.let { m ->
                    val w = m.groupValues[1]
                    val canon = when (w) {
                        "T" -> "tbsp"
                        "t" -> "tsp"
                        else -> SYN[w.lowercase()]
                    }
                    if (canon != null) {
                        unit = canon
                        rest = rest.substring(m.range.last + 1)
                        if (canon == "can") {
                            canned = true
                            CAN_SIZE.find(rest)?.let { c ->
                                val n = num(c.groupValues[1])
                                if (n != null) {
                                    qty = (qty ?: 1.0) * n
                                    unit = SYN[c.groupValues[2].lowercase()]
                                    rest = rest.substring(c.range.last + 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        rest = rest.trim().replace(Regex("^of\\s+", RegexOption.IGNORE_CASE), "")

        unitHint?.takeIf { it.isNotBlank() }?.let { u -> unit = SYN[u.lowercase()] ?: SYN[u.lowercase().trimEnd('s')] ?: u.lowercase() }
        quantityHint?.let { qty = it }
        val source = foodHint?.takeIf { it.isNotBlank() } ?: rest
        var query = cleanQuery(source)
        if (canned && "canned" !in query.split(' ')) query = "$query canned".trim()
        val finalUnit = unit
        val kind = when (finalUnit) {
            in MASS -> UnitKind.MASS
            in VOLUME -> UnitKind.VOLUME
            else -> UnitKind.COUNT
        }
        return ParsedIngredient(qty, finalUnit, kind, query, hintsOf(source))
    }
}

/** Finds the best USDA food for a recipe line and converts its amount to grams. */
class IngredientMatcher(private val usda: UsdaDatabase) {

    private val bad = Regex(
        "\\b(meatless|imitation|substitute|vegetarian|fat free|nonfat|low fat|lowfat|reduced|light|no|without|" +
            "baby food|infant|toddler|topping|flavored|mix|spread|yolk|turkey|sandwich|salad|dip|soup|breaded|" +
            "battered|fried|nugget|patty|tender|pickled|leaf|leaves|baby|low|bread|cake|muffin|pie|cookie|casserole|" +
            "stew|pizza|burrito|quiche)\\b",
    )
    private val brand = Regex("\\b[A-Z][A-Z'&]{3,}\\b")
    private val rawWords = Regex("\\b(raw|uncooked)\\b")
    private val category = setOf(
        "soup", "spice", "cereal", "beverage", "snack", "sauce", "candy", "babyfood", "restaurant", "fast food",
        "alcoholic beverage", "nut", "seed", "fish", "leavening agent", "sweetener",
    )
    private val forms = setOf(
        "oil", "butter", "flour", "milk", "juice", "paste", "powder", "extract", "syrup", "sauce", "spread", "meal",
        "cream", "dried", "dehydrated", "canned", "frozen", "cooked", "boiled", "baked", "roasted", "stewed",
        "braised", "grilled", "steamed", "candied", "sweetened", "sticks",
    )
    private val countWords = Regex("medium|whole|large|each|\\b(egg|clove|slice|fruit)\\b")

    fun match(ing: MealieIngredient): IngredientMatch {
        val parsed = IngredientParser.parse(ing.text, ing.quantity, ing.unit, ing.food)
        val best = best(parsed) ?: return IngredientMatch(null, estimated = false, missingAmount = parsed.quantity == null)
        val food = usda.food(best.id) ?: return IngredientMatch(null, false, parsed.quantity == null)
        val (portion, estimated) = resolve(best, parsed)
        val qty = parsed.quantity ?: 1.0
        val item = RecipeItem(
            recipeId = 0,
            source = FoodSource.USDA.key,
            refId = food.id,
            name = food.name,
            quantity = qty,
            unitLabel = portion.label,
            unitGrams = portion.grams,
            grams = qty * portion.grams,
            per100 = NutrientCodec.encode(food.per100g),
        )
        return IngredientMatch(item, estimated, parsed.quantity == null)
    }

    // ---------- choosing the food ----------

    private fun segments(d: String): List<List<String>> =
        d.split(',').map { seg -> IngredientParser.words(seg).map(IngredientParser::singular) }.filter { it.isNotEmpty() }

    private fun primaryWords(d: String): List<String> {
        val segs = segments(d)
        if (segs.isEmpty()) return emptyList()
        return if (segs[0].joinToString(" ") in category && segs.size > 1) segs[1] else segs[0]
    }

    private fun coverage(d: String, tokens: Set<String>): Double {
        val segs = segments(d)
        if (segs.isEmpty() || tokens.isEmpty()) return 0.0
        val leadIsCategory = segs[0].joinToString(" ") in category && segs.size > 1
        val lead = if (leadIsCategory) 2 else 1
        val weight = HashMap<String, Double>()
        segs.forEachIndexed { i, seg ->
            val w = when {
                i < lead -> 1.0
                i == lead -> 0.7
                else -> 0.4
            }
            var after = false
            for (word in seg) {
                if (word == "with" || word == "from" || word == "in" || word == "without") {
                    after = true
                } else {
                    val ww = if (after) 0.2 else w
                    weight[word] = maxOf(weight[word] ?: 0.0, ww)
                }
            }
        }
        return tokens.sumOf { weight[it] ?: 0.0 } / tokens.size
    }

    private fun best(p: ParsedIngredient): UsdaDatabase.Candidate? {
        val words = p.query.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        val tries = ArrayList<String>()
        tries += p.query
        if (words.size > 1) {
            val core = words.filter { it != "canned" }
            if (core.isNotEmpty()) {
                tries += core.drop(1).joinToString(" ")
                tries += core.last()
                tries += core.first()
            }
        }
        val cooked = "cooked" in words
        for (t in tries) {
            if (t.isBlank()) continue
            val rows = runCatching { usda.candidates(t, 60) }.getOrDefault(emptyList())
            if (rows.isEmpty()) continue
            val tt = t.split(' ').filter { it.isNotBlank() }.toSet()
            return rows.maxByOrNull { score(it, tt, p, cooked) }
        }
        return null
    }

    private fun score(r: UsdaDatabase.Candidate, tt: Set<String>, p: ParsedIngredient, cooked: Boolean): Double {
        val d = r.description.lowercase()
        var sc = 4 * coverage(d, tt)
        val prim = primaryWords(d)
        sc += if (prim.isNotEmpty() && prim[0] in tt) 0.5 else -0.5
        val all = segments(d).flatten().toSet()
        if (brand.containsMatchIn(r.description.replace("NFS", "").replace("NS ", ""))) sc -= 3
        sc -= 0.3 * prim.count { it !in tt && it !in p.hints }
        sc -= 2 * all.count { it in forms && it !in tt && it !in p.hints }
        sc += 0.5 * p.hints.toSet().count { it in all }
        if (!cooked && rawWords.containsMatchIn(d) && "canned" !in tt) sc += 1.2
        if (" and " in d && "and" !in tt) sc -= 1.5
        if (cooked && "cooked" in d) sc += 1.0
        if ("canned" in tt && "canned" in d) sc += 1.0
        val q = p.query
        val badHit = bad.findAll(d).map { it.value }.any { m ->
            m !in q && !(m == "soup" && Regex("broth|stock|bouillon").containsMatchIn(q))
        }
        if (badHit) sc -= 4
        if (r.source == 0 || r.source == 1) sc += 0.3
        if (hasPortion(r, p)) sc += 2
        sc -= d.length / 50.0
        return sc
    }

    // ---------- converting the amount ----------

    private data class LabelUnit(val amount: Double, val unit: String?, val rest: String)

    private fun labelUnit(label: String): LabelUnit {
        val m = Regex("""^\s*(\d+\s+\d+/\d+|\d+/\d+|\d*\.\d+|\d+)?\s*(.*)$""").find(label)
        val n = m?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { IngredientParser.num(it) } ?: 1.0
        val rest = (m?.groupValues?.getOrNull(2) ?: label).lowercase()
        val tok = Regex("""^(fl\.?\s*oz|[a-z]+)""").find(rest)?.groupValues?.get(1)
        val unit = when {
            tok == null -> null
            tok.startsWith("fl") -> "fl oz"
            tok == "tablespoon" -> "tbsp"
            tok == "teaspoon" -> "tsp"
            else -> IngredientParser.SYN[tok]
        }
        return LabelUnit(if (n > 0) n else 1.0, unit, rest)
    }

    private fun isMassOrVolume(u: String?) = u != null && (u in IngredientParser.MASS || u in IngredientParser.VOLUME)

    private fun hasPortion(r: UsdaDatabase.Candidate, p: ParsedIngredient): Boolean = when (p.kind) {
        UnitKind.MASS -> true
        UnitKind.VOLUME -> r.portions.any { labelUnit(it.label).unit in IngredientParser.VOLUME }
        UnitKind.COUNT -> r.portions.any { portion ->
            val lu = labelUnit(portion.label)
            !isMassOrVolume(lu.unit) && (
                p.unit == null || p.unit == lu.unit || lu.rest.contains(p.unit) ||
                    p.unit in setOf("large", "medium", "small", "whole")
                )
        }
    }

    private fun resolve(r: UsdaDatabase.Candidate, p: ParsedIngredient): Pair<Portion, Boolean> {
        val unit = p.unit
        if (p.kind == UnitKind.MASS && unit != null) {
            return Portion(unit, IngredientParser.MASS.getValue(unit)) to false
        }
        if (p.kind == UnitKind.VOLUME && unit != null) {
            val ml = IngredientParser.VOLUME.getValue(unit)
            var gramsPerMl: Double? = null
            for (portion in r.portions) {
                val lu = labelUnit(portion.label)
                val luMl = lu.unit?.let { IngredientParser.VOLUME[it] } ?: continue
                if (lu.unit == unit) {
                    return (if (lu.amount == 1.0) portion else Portion(unit, portion.grams / lu.amount)) to false
                }
                if (gramsPerMl == null) gramsPerMl = portion.grams / (lu.amount * luMl)
            }
            gramsPerMl?.let { return Portion(unit, ml * it) to false }
            // Borrow the density of a similar food that has a volume portion.
            val head = r.description.lowercase().split(',').first()
            for (other in runCatching { usda.candidates(head, 40) }.getOrDefault(emptyList())) {
                for (portion in other.portions) {
                    val lu = labelUnit(portion.label)
                    val luMl = lu.unit?.let { IngredientParser.VOLUME[it] } ?: continue
                    return Portion(unit, ml * portion.grams / (lu.amount * luMl)) to true
                }
            }
            return Portion(unit, ml) to true
        }
        // Counted items: "3 eggs", "4 cloves garlic", "1 medium onion".
        for (portion in r.portions) {
            val lu = labelUnit(portion.label)
            if (unit != null && (lu.unit == unit || lu.rest.contains(unit))) return Portion(portion.label, portion.grams / lu.amount) to false
        }
        for (portion in r.portions) {
            val lu = labelUnit(portion.label)
            if (!isMassOrVolume(lu.unit) && countWords.containsMatchIn(lu.rest)) return Portion(portion.label, portion.grams / lu.amount) to false
        }
        for (portion in r.portions) {
            val lu = labelUnit(portion.label)
            if (!isMassOrVolume(lu.unit)) return Portion(portion.label, portion.grams / lu.amount) to false
        }
        if (unit == "can") return Portion("can (about 15 oz)", 425.0) to true
        val prim = primaryWords(r.description.lowercase())
        val searches = if (prim.isEmpty()) emptyList() else listOf(prim.joinToString(" ")) + prim
        for (t in searches) {
            for (other in runCatching { usda.candidates(t, 40) }.getOrDefault(emptyList())) {
                if (other.id == r.id) continue
                for (portion in other.portions) {
                    val lu = labelUnit(portion.label)
                    if (!isMassOrVolume(lu.unit) &&
                        (unit == null || unit == lu.unit || lu.rest.contains(unit) || Regex("medium|whole").containsMatchIn(lu.rest))
                    ) {
                        return Portion(portion.label + " (similar food)", portion.grams / lu.amount) to true
                    }
                }
            }
        }
        r.portions.firstOrNull()?.let { return Portion(it.label, it.grams / labelUnit(it.label).amount) to true }
        return Portion("g", 100.0) to true
    }
}
