package com.moblized.nutritrack.data

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
    /** The food part of the line ("canned San Marzano tomatoes"), and anything after a comma or in a note. */
    val food: String = "",
    val note: String = "",
    /** True for "1 can ..." lines and canned foods. */
    val canned: Boolean = false,
)

data class IngredientMatch(
    val item: RecipeItem?,
    /** The gram amount is a guess (no matching portion in the USDA data). */
    val estimated: Boolean,
    /** The recipe line has no amount ("salt to taste"). */
    val missingAmount: Boolean,
    /** The line's unit ("12 drops", "1 bunch") has no gram weight for this food, so the amount needs setting. */
    val unconverted: Boolean = false,
)

/** Parses recipe lines like "1 (14.5 oz) can diced tomatoes" or "2 ½ cups flour". */
object IngredientParser {
    val MASS = mapOf("g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "oz" to 28.349523, "lb" to 453.59237)
    val VOLUME = mapOf(
        "ml" to 1.0, "l" to 1000.0, "tsp" to 4.92892, "tbsp" to 14.7868, "cup" to 236.588,
        "fl oz" to 29.5735, "pint" to 473.176, "quart" to 946.353, "gallon" to 3785.41,
        "pinch" to 0.31, "dash" to 0.62, "drop" to 0.05,
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
            "leaf" to "leaf leaves", "drop" to "drop drops", "scoop" to "scoop scoops",
            "packet" to "packet packets", "envelope" to "envelope envelopes",
        ).forEach { (canon, words) -> words.split(" ").forEach { put(it, canon) } }
    }

    /** Count units that can trail the food: "3 garlic cloves", "2 celery stalks". */
    val COUNT_UNITS = setOf("clove", "slice", "piece", "stick", "head", "sprig", "stalk", "leaf", "bunch", "scoop", "packet", "envelope")

    val DESCRIPTORS: Set<String> = (
        "chopped diced minced sliced thinly finely roughly coarsely fresh freshly large medium small extra big " +
            "divided optional peeled seeded cored grated shredded melted softened cubed crushed packed heaping level " +
            "about approximately plus more taste to for serving garnish room temperature cold warm boneless skinless " +
            "trimmed rinsed drained halved quartered beaten lightly well firmly cut into pieces piece inch inches of " +
            "the a an or and your favorite good quality organic uncooked raw dry leaf leaves sprig sprigs fine coarse " +
            "kosher flaky lukewarm"
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
    private val PCT = Regex("""(\d+(?:\.\d+)?)\s*%""")
    private val TOKEN = Regex("""pct[0-9.]+|[a-z]+""")
    /** "milk or water", "salt + pepper", "erythritol/swerve" (but not "1/2"). */
    private val ALT = Regex("""\s+or\s+|\s+\+\s+|(?<=[A-Za-z])\s*/\s*(?=[A-Za-z])""", RegexOption.IGNORE_CASE)
    val SEGMENT = Regex(",| - | – |;")
    private val WS = Regex("\\s+")
    private val TRAILING_WORD = Regex("""^(.*\S)\s+([A-Za-z]+)\s*$""")

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
            t = stripMarks(t).replace('⁄', '/')
        }
        return t
    }

    fun singular(w: String): String = when {
        w.length <= 3 -> w
        w.endsWith("oes") -> w.dropLast(2)
        w.endsWith("ies") -> w.dropLast(3) + "y"
        // leaves, halves, loaves; but cloves, olives, chives keep their e
        w.length >= 5 && w.endsWith("ves") && w.substring(w.length - 5, w.length - 3) in setOf("ea", "oa", "al", "el") -> w.dropLast(3) + "f"
        w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Lowercase words; "2%" becomes the token "pct2" so fat levels can be matched. */
    fun words(text: String): List<String> =
        TOKEN.findAll(PCT.replace(text.lowercase()) { " pct" + it.groupValues[1] + " " }).map { it.value }.toList()

    fun isPct(w: String): Boolean = w.length > 3 && w.startsWith("pct") && w[3].isDigit()

    /** "milk or water" -> "milk"; "chicken or vegetable broth" -> "chicken broth"; "Erythritol/Swerve" -> "Erythritol". */
    fun firstChoice(text: String): String {
        val parts = text.split(ALT, limit = 2)
        if (parts.size < 2) return text
        val a = parts[0].trim()
        val b = parts[1].trim()
        val aw = a.split(WS).filter { it.isNotEmpty() }
        val bw = b.split(WS).filter { it.isNotEmpty() }
        return if (aw.size == 1 && bw.size >= 2 && b.firstOrNull()?.isLetter() == true) a + " " + bw.drop(1).joinToString(" ") else a
    }

    /** Keeps a short note ("low-fat", "turkey"); drops alternatives ("or 1 scoop") and instructions. */
    fun cleanNote(note: String): String {
        var n = note.trim()
        if (n.startsWith("(") && n.endsWith(")") && n.length >= 2) n = n.substring(1, n.length - 1).trim()
        if (Regex("^(or|and/or)\\b", RegexOption.IGNORE_CASE).containsMatchIn(n)) return ""
        n = firstChoice(n.split(SEGMENT, limit = 2).first()).trim()
        return if (words(n).count { it !in DESCRIPTORS } > 4) "" else n
    }

    private fun hintsOf(text: String): List<String> =
        words(text).filter { it in DESCRIPTORS && it !in STOP }.map(::singular)

    private fun cleanQuery(text: String): String {
        var t = text.lowercase().replace(Regex("\\([^)]*\\)"), " ")
        t = t.split(SEGMENT, limit = 2).first()
        return words(t).filter { it !in DESCRIPTORS }.take(4).joinToString(" ") { singular(it) }
    }

    fun parse(
        text: String,
        quantityHint: Double? = null,
        unitHint: String? = null,
        foodHint: String? = null,
        noteHint: String? = null,
    ): ParsedIngredient {
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
        val source = firstChoice(foodHint?.takeIf { it.isNotBlank() } ?: rest)
        var query = cleanQuery(source)
        if (canned && "canned" !in query.split(' ')) query = "$query canned".trim()
        // Mealie keeps the food and a note apart ("pepperoni" + "turkey"); plain lines put extras after a comma.
        var (food, note) = if (!foodHint.isNullOrBlank()) {
            foodHint to noteHint.orEmpty()
        } else {
            val parts = rest.replace(Regex("^\\s*\\([^)]*\\)\\s*"), "").split(SEGMENT, limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }
        // "2% milk or 215 g fat-free" -> "2% milk"; a note that is only an alternative ("or 1 scoop") is dropped.
        food = firstChoice(food)
        note = cleanNote(note)
        // "3 garlic cloves", "2 celery stalks": the count unit comes last.
        if (unit == null) {
            TRAILING_WORD.find(food.trim())?.let { m ->
                val last = SYN[m.groupValues[2].lowercase()]
                if (last != null && last in COUNT_UNITS) {
                    unit = last
                    food = m.groupValues[1]
                }
            }
        }
        val finalUnit = unit
        val kind = when (finalUnit) {
            in MASS -> UnitKind.MASS
            in VOLUME -> UnitKind.VOLUME
            else -> UnitKind.COUNT
        }
        val isCanned = canned || "canned" in words(food + " " + note) || Regex("\\bcans?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return ParsedIngredient(qty, finalUnit, kind, query, hintsOf(food + " " + note), food.trim(), note.trim(), isCanned)
    }
}

/** Where the matcher looks up foods; the bundled USDA database in the app, a JDBC copy in tests. */
interface FoodCandidateSource {
    fun candidates(query: String, limit: Int): List<UsdaDatabase.Candidate>
    fun food(id: Long): Food?
}

/**
 * Finds the best USDA food for a recipe line and converts its amount to grams.
 *
 * The food's last word is treated as what it is (the head: "sea *salt*", "turkey *pepperoni*"), other food
 * words describe it, and Mealie notes ("low-fat", "turkey") count a bit less. Candidates come from searching
 * the whole name and each word, must contain the head, and are scored by how well their USDA name covers
 * those words. When nothing contains the head ("green food coloring"), there is no match rather than a wrong one.
 */
class IngredientMatcher(private val usda: FoodCandidateSource) {

    private val bad = Regex(
        "\\b(meatless|imitation|substitute|vegetarian|fat free|nonfat|low fat|lowfat|reduced|light|no|without|" +
            "baby food|infant|toddler|topping|flavored|mix|spread|yolk|turkey|sandwich|salad|dip|soup|breaded|" +
            "battered|fried|nugget|patty|tender|pickled|leaf|leaves|baby|low|bread|cake|muffin|pie|cookie|casserole|" +
            "stew|pizza|burrito|quiche|stuffed|fat added|added fat|skin eaten|sheep|goat|buffalo|human|camel|bison|elk|deer|" +
            "venison|moose|caribou|whale|seal|bear|alaska native|non-fat|fat-free|skim|flavor|deli|prepackaged|luncheon)\\b",
    )
    /** Fat-level words that are fine once the line asks for any reduced-fat version ("skim", "2%"). */
    private val fatLevels = setOf("reduced", "low fat", "lowfat", "low", "light", "fat free", "nonfat", "non-fat", "fat-free", "skim")
    private val brand = Regex("\\b[A-Z][A-Z'&]{3,}\\b")
    private val rawWords = Regex("\\b(raw|uncooked)\\b")
    private val includesNote = Regex("\\s*\\((?:includes|Includes)[^)]*\\)")
    private val category = setOf(
        "soup", "spice", "cereal", "beverage", "snack", "sauce", "candy", "babyfood", "restaurant", "fast food",
        "alcoholic beverage", "nut", "seed", "fish", "leavening agent", "sweetener",
        "pepper", "cheese", "bean", "oil", "lettuce", "cabbage", "mushroom", "chicken", "beef", "pork", "turkey", "lamb",
    )
    private val meats = setOf("chicken", "beef", "pork", "turkey", "lamb")
    private val baseCategory = setOf(
        "soup", "spice", "cereal", "beverage", "snack", "sauce", "candy", "babyfood", "restaurant", "fast food",
        "alcoholic beverage", "nut", "seed", "fish", "leavening agent", "sweetener",
    )
    private val forms = setOf(
        "oil", "butter", "flour", "milk", "juice", "paste", "powder", "extract", "syrup", "sauce", "spread", "meal",
        "cream", "dried", "dehydrated", "canned", "frozen", "cooked", "boiled", "baked", "roasted", "stewed",
        "braised", "grilled", "steamed", "candied", "sweetened", "sticks", "sauteed", "broiled", "poached",
        "scrambled", "fried", "pickled", "rotisserie", "roll", "evaporated", "condensed", "malted", "buttermilk", "powdered",
    )
    /** A different state of the food, wherever it appears in the name ("Milk, buttermilk, dried"). */
    private val states = setOf("dried", "dehydrated", "powder", "powdered", "evaporated", "condensed", "frozen")
    /** Flavours nobody asked for ("Yogurt, Greek, strawberry" for Greek yogurt). */
    private val flavors = setOf(
        "strawberry", "vanilla", "chocolate", "blueberry", "fruit", "peach", "cherry", "raspberry",
        "honey", "coffee", "caramel", "mocha", "lemon", "lime", "orange", "coconut", "flavored",
    )
    /** USDA wording for "low-fat" and friends. */
    private val synonyms = listOf(
        Regex("part[- ]skim") to listOf("low", "fat"),
        Regex("\\blowfat\\b") to listOf("low", "fat"),
        Regex("reduced fat|\\blight\\b") to listOf("low", "fat"),
        Regex("fat[- ]free|\\bskim\\b|\\bnonfat\\b|non-fat") to listOf("nonfat", "low", "fat", "free", "pct0"),
        Regex("\\bdry\\b|\\bdried\\b|\\bdehydrated\\b") to listOf("powder"),
        Regex("sugar substitute") to listOf("sweetener"),
    )
    /** Recipe words USDA spells differently: recipe word -> USDA word. */
    private val aliases = mapOf(
        "zest" to "peel", "jello" to "gelatin", "courgette" to "zucchini", "aubergine" to "eggplant",
        "yoghurt" to "yogurt", "garbanzo" to "chickpea",
    )
    /** "red pepper flakes": when nothing is called flakes, the pepper is what matters. */
    private val headForms = setOf("flake", "chunk", "floret", "wedge", "strip", "cube", "bit", "crumble", "shaving", "spear", "ring", "round", "half")
    /** Words USDA puts after the food ("Egg, white", "Milk, dry"), so they needn't lead the name. */
    private val parts = setOf(
        "white", "yolk", "breast", "thigh", "leg", "wing", "drumstick", "fillet", "filet", "loin", "tenderloin",
        "juice", "zest", "peel", "powder",
    )
    private val sizes = setOf("large", "medium", "small", "whole")
    private val cuts = setOf("sliced", "chopped", "shredded", "diced", "grated", "minced", "crumbled", "cubed", "packed")
    /** The everyday version when nothing more was asked: granulated sugar, "Milk, NFS". */
    private val defaults = setOf("granulated", "plain", "regular", "table", "nfs")
    private val stateTerms = setOf("cooked", "raw", "canned", "frozen", "dried", "boiled", "baked", "roasted", "steamed", "grilled")
    private val countPreference = listOf("medium", "whole", "each", "fruit", "egg", "clove", "slice", "large", "small")

    /** Weighted search words for a parsed line. */
    private data class Query(
        val terms: LinkedHashMap<String, Double>,
        val hints: List<String>,
        val head: String?,
        val foodWords: List<String>,
        val cooked: Boolean,
        val canned: Boolean,
        /** A second head to try when nothing contains the first ("pepper" for "red pepper flakes"). */
        val fallback: String?,
        /** The line asks for a reduced-fat version ("skim", "low-fat", "2%"). */
        val lean: Boolean,
    ) {
        val joined: String get() = terms.keys.joinToString(" ")
    }

    private data class Resolved(val portion: Portion, val estimated: Boolean, val unconverted: Boolean = false)

    private fun query(p: ParsedIngredient): Query {
        val desc = IngredientParser.DESCRIPTORS
        val foodText = p.food.replace(Regex("\\([^)]*\\)"), " ").split(Regex(",| - |;")).first()
        var fw = IngredientParser.words(foodText).filter { it !in desc }.map(IngredientParser::singular).take(5)
        if (fw == listOf("vanilla")) fw = listOf("vanilla", "extract") // "1 tsp vanilla"
        val nw = IngredientParser.words(p.note).filter { it !in desc }.map(IngredientParser::singular).take(3)
        val nouns = fw.filterNot(IngredientParser::isPct)
        val head = nouns.lastOrNull() ?: nw.lastOrNull { !IngredientParser.isPct(it) }
        val terms = LinkedHashMap<String, Double>()
        fw.forEach { w -> terms[w] = maxOf(terms[w] ?: 0.0, if (w == head) 1.5 else 1.0) }
        nw.forEach { w -> if (w !in terms) terms[w] = 0.8 }
        terms.remove("lowfat")?.let { w ->
            if ("low" !in terms) terms["low"] = w
            if ("fat" !in terms) terms["fat"] = w
        }
        val canned = "canned" in terms || p.canned
        if (canned && "canned" !in terms) terms["canned"] = 0.5
        val fallback = if (head in headForms && nouns.size >= 2) nouns[nouns.size - 2] else null
        val pct = terms.keys.filter(IngredientParser::isPct).mapNotNull { it.drop(3).toDoubleOrNull() }
        val lean = listOf("skim", "nonfat", "lowfat", "reduced", "light").any { it in terms } ||
            ("low" in terms && "fat" in terms) || ("fat" in terms && "free" in terms) || pct.any { it < 3 }
        return Query(terms, p.hints, head, fw, "cooked" in terms, canned, fallback, lean)
    }

    fun match(ing: MealieIngredient): IngredientMatch {
        val parsed = IngredientParser.parse(ing.text, ing.quantity, ing.unit, ing.food, ing.note)
        val (best, q) = best(query(parsed), parsed)
            ?: return IngredientMatch(null, estimated = false, missingAmount = parsed.quantity == null)
        val food = usda.food(best.id) ?: return IngredientMatch(null, false, parsed.quantity == null)
        // Prefer a portion that matches the line's words, e.g. "1 cup, shredded" for shredded cheese.
        val ordered = best.copy(portions = best.portions.sortedBy { pt -> if (q.hints.any { it in pt.label.lowercase() }) 0 else 1 })
        val resolved = resolve(ordered, parsed, q.head)
        val portion = resolved.portion
        val qty = if (resolved.unconverted) 1.0 else parsed.quantity ?: 1.0
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
        return IngredientMatch(item, resolved.estimated, parsed.quantity == null, resolved.unconverted)
    }

    // ---------- choosing the food ----------

    private fun segments(d: String): List<List<String>> =
        d.split(',').map { seg -> IngredientParser.words(seg).map(IngredientParser::singular) }.filter { it.isNotEmpty() }

    private fun primaryWords(segs: List<List<String>>): List<String> {
        if (segs.isEmpty()) return emptyList()
        return if (segs[0].joinToString(" ") in category && segs.size > 1) segs[1] else segs[0]
    }

    /** How strongly each word of a USDA name says what the food is: the lead part counts most. */
    private fun segmentWeights(d: String, segs: List<List<String>>): Map<String, Double> {
        if (segs.isEmpty()) return emptyMap()
        val lead = if (segs[0].joinToString(" ") in category && segs.size > 1) 2 else 1
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
                    weight[word] = maxOf(weight[word] ?: 0.0, if (after) 0.2 else w)
                }
            }
        }
        for ((rx, extra) in synonyms) {
            if (rx.containsMatchIn(d)) extra.forEach { e -> weight[e] = maxOf(weight[e] ?: 0.0, 0.7) }
        }
        for ((word, alias) in aliases) {
            weight[alias]?.let { a -> weight[word] = maxOf(weight[word] ?: 0.0, a) }
        }
        return weight
    }

    private fun covers(r: UsdaDatabase.Candidate, word: String): Boolean {
        val d = includesNote.replace(r.description, "").lowercase()
        return word in segmentWeights(d, segments(d))
    }

    /** How a query word is searched: "2%" as "2", aliases as USDA spells them, "blueberr" to find "blueberries". */
    private fun fts(t: String): String {
        if (IngredientParser.isPct(t)) return t.drop(3)
        val a = aliases[t] ?: t
        return if (a.length > 4 && (a.last() == 'y' || a.last() == 'f')) a.dropLast(1) else a
    }

    private fun best(q0: Query, p: ParsedIngredient): Pair<UsdaDatabase.Candidate, Query>? {
        var q = q0
        val ws = q.terms.keys.toList()
        if (ws.isEmpty()) return null
        val searches = ArrayList<String>()
        searches += ws.joinToString(" ") { fts(it) }
        q.head?.let { searches += fts(it) }
        q.fallback?.let { searches += fts(it) }
        ws.zipWithNext().forEach { (a, b) -> searches += "${fts(a)} ${fts(b)}" }
        searches += ws.map { fts(it) }
        val seen = HashSet<Long>()
        var cands = ArrayList<UsdaDatabase.Candidate>()
        for (s in searches) {
            for (c in runCatching { usda.candidates(s, 60) }.getOrDefault(emptyList())) {
                if (seen.add(c.id)) cands += c
            }
        }
        if (cands.isEmpty()) return null
        // It has to be the food asked for: no "Beet greens" for green food coloring.
        val head = q.head
        if (head != null) {
            var covered = cands.filter { covers(it, head) }
            val fb = q.fallback
            if (covered.isEmpty() && fb != null) {
                val terms = LinkedHashMap(q.terms)
                terms[fb] = maxOf(terms[fb] ?: 0.0, 1.5)
                q = q.copy(terms = terms, head = fb)
                covered = cands.filter { covers(it, fb) }
            }
            if (covered.isEmpty()) return null
            cands = ArrayList(covered)
        }
        val chosen = cands.maxByOrNull { score(it, q, p) } ?: return null
        return chosen to q
    }

    private fun score(r: UsdaDatabase.Candidate, q: Query, p: ParsedIngredient): Double {
        val terms = q.terms
        val d = includesNote.replace(r.description, "").lowercase()
        val segs = segments(d)
        val wts = segmentWeights(d, segs)
        val total = terms.values.sum()
        var sc = 6 * terms.entries.sumOf { (t, w) -> w * (wts[t] ?: 0.0) } / total
        val prim = primaryWords(segs)
        val head = q.head
        if (head != null && head !in wts) {
            sc -= 2.5 // it has to be the food asked for
        } else if (head != null && (wts[head] ?: 0.0) < 1.0 && head !in parts) {
            sc -= 1.0 // ...as what it is, not a side detail ("Yogurt, whole milk" for milk)
        }
        val notesOk = terms.filter { it.value < 1.0 }.keys.all { (wts[it] ?: 0.0) > 0 }
        if (prim.isNotEmpty() && prim == q.foodWords && notesOk) {
            sc += if (q.foodWords.size == 1) 1.5 else 0.8 // the plain thing, e.g. "Water, tap" for water
        } else if (prim.isNotEmpty() && head != null && prim.last() == head && prim.size <= q.foodWords.size + 1) {
            sc += 0.6
        }
        val rawWordsInName = IngredientParser.words(d).toSet()
        for (t in terms.keys) {
            // unsalted vs salted, unsweetened vs sweetened
            if (t.startsWith("un") && t.drop(2) in rawWordsInName && t !in rawWordsInName) sc -= 2.5
        }
        Regex("\\bwith\\b(.*)").find(d)?.let { m ->
            val extra = IngredientParser.words(m.groupValues[1]).count {
                it !in terms && it !in q.hints && it !in setOf("and", "or", "salt", "added") && !IngredientParser.isPct(it)
            }
            sc -= 0.5 * minOf(3, extra)
        }
        val all = segs.flatten().toSet()
        if (brand.containsMatchIn(r.description.replace("NFS", "").replace("NS ", "").replace("USDA", ""))) sc -= 1.5
        sc -= 0.25 * prim.count { it !in terms && it !in q.hints }
        val leadWords = segs.take(2).flatten().toSet()
        sc -= 2 * leadWords.count { it in forms && it !in terms && it !in q.hints }
        val later = segs.drop(2).flatten().toSet() - leadWords
        sc -= 0.8 * later.count { it in states && it !in terms && it !in q.hints }
        if (head == null || (head !in flavors && head != "extract" && head != "flavoring")) {
            if (all.any { it in flavors && it !in terms && it !in q.hints }) sc -= 1.0
        }
        val hintSet = q.hints.map { if (it == "uncooked") "raw" else it }.toSet()
        sc += 0.5 * hintSet.count { it in all }
        val processed = q.cooked || q.canned || listOf("frozen", "dried", "smoked", "roasted", "toasted").any { it in terms }
        if (!processed && rawWords.containsMatchIn(d)) sc += 0.6
        if (" and " in d && "and" !in terms) sc -= 1.5
        if (q.cooked && "cooked" in d) sc += 1.0
        if (q.canned && "canned" in d) sc += 1.0
        val joined = q.joined
        val badHit = bad.findAll(d).map { it.value }.any { m ->
            val allowed = m in terms || joined.contains(m) ||
                (m == "soup" && Regex("broth|stock|bouillon").containsMatchIn(joined)) ||
                (q.lean && m in fatLevels) || // "2% milk" asks for reduced fat
                ((m == "deli" || m == "prepackaged") && "sliced" in q.hints) // sliced turkey is deli turkey
            !allowed
        }
        if (badHit) sc -= 4
        val first = segs.firstOrNull()?.joinToString(" ")
        if (first != null && first in meats && first !in terms) sc -= 0.8 // "Beef, bacon" when asked for bacon
        if (terms.keys.count { it !in stateTerms } == 1 &&
            (segs.drop(1).take(1).any { s -> s.any { it in defaults } } || segs.drop(1).any { "nfs" in it })
        ) {
            sc += 0.4
        }
        if (r.source == 0 || r.source == 1 || r.source == 3) sc += 0.3
        if (hasPortion(r, p)) sc += 0.8
        // "1/2 cup sliced almonds": a "1 cup, sliced" portion weighs what was asked.
        val cutHints = q.hints.filter { it in cuts }
        if (p.kind != UnitKind.MASS && cutHints.isNotEmpty() &&
            r.portions.any { pt -> cutHints.any { it in pt.label.lowercase() } }
        ) {
            sc += 0.4
        }
        sc -= d.length / 60.0
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

    private fun resolve(r: UsdaDatabase.Candidate, p: ParsedIngredient, head: String?): Resolved {
        val unit = p.unit
        if (p.kind == UnitKind.MASS && unit != null) {
            return Resolved(Portion(unit, IngredientParser.MASS.getValue(unit)), false)
        }
        if (p.kind == UnitKind.VOLUME && unit != null) {
            val ml = IngredientParser.VOLUME.getValue(unit)
            var gramsPerMl: Double? = null
            for (portion in r.portions) {
                val lu = labelUnit(portion.label)
                val luMl = lu.unit?.let { IngredientParser.VOLUME[it] } ?: continue
                if (lu.unit == unit) {
                    return Resolved(if (lu.amount == 1.0) portion else Portion(unit, portion.grams / lu.amount), false)
                }
                if (gramsPerMl == null) gramsPerMl = portion.grams / (lu.amount * luMl)
            }
            gramsPerMl?.let { return Resolved(Portion(unit, ml * it), false) }
            // Borrow the density of a similar food that has a volume portion.
            val lead = r.description.lowercase().split(',').first()
            for (other in runCatching { usda.candidates(lead, 40) }.getOrDefault(emptyList())) {
                for (portion in other.portions) {
                    val lu = labelUnit(portion.label)
                    val luMl = lu.unit?.let { IngredientParser.VOLUME[it] } ?: continue
                    return Resolved(Portion(unit, ml * portion.grams / (lu.amount * luMl)), true)
                }
            }
            return Resolved(Portion(unit, ml), true)
        }
        // Counted items: "3 eggs", "4 cloves garlic", "1 medium onion", "1 scoop".
        for (portion in r.portions) {
            val lu = labelUnit(portion.label)
            if (unit != null && (lu.unit == unit || lu.rest.contains(unit))) return Resolved(Portion(portion.label, portion.grams / lu.amount), false)
        }
        // A bare count ("2 zucchini") means a medium one when the data has sizes (eggs come large).
        // A named unit ("1 scoop", "12 drops") only ever matches a portion with that name.
        val sized = unit == null || unit in sizes
        val named = head?.let { Regex("\\b" + Regex.escape(it) + "s?\\b") }
        if (sized) {
            val prefs = (if (head == "egg") listOf("large") else emptyList()) + countPreference
            for (pref in prefs) {
                val word = Regex("\\b$pref\\b")
                for (portion in r.portions) {
                    val lu = labelUnit(portion.label)
                    if (!isMassOrVolume(lu.unit) && word.containsMatchIn(lu.rest)) return Resolved(Portion(portion.label, portion.grams / lu.amount), false)
                }
            }
            for (portion in r.portions) {
                val lu = labelUnit(portion.label)
                if (!isMassOrVolume(lu.unit)) return Resolved(Portion(portion.label, portion.grams / lu.amount), false)
            }
        }
        if (unit == "can") return Resolved(Portion("can (about 15 oz)", 425.0), true)
        // Borrow a count portion from the most similar food ("Peppers, sweet, red" for a red bell pepper).
        val segs = segments(r.description.lowercase())
        val prim = if (segs.isEmpty()) emptyList() else if (segs[0].joinToString(" ") in baseCategory && segs.size > 1) segs[1] else segs[0]
        val searches = if (prim.isEmpty()) emptyList() else listOf(segs.take(2).flatten().joinToString(" "), prim.joinToString(" ")) + prim
        val own = segs.flatten().toSet()
        val seen = hashSetOf(r.id)
        val found = ArrayList<UsdaDatabase.Candidate>()
        for (t in searches) {
            for (other in runCatching { usda.candidates(t, 40) }.getOrDefault(emptyList())) {
                if (seen.add(other.id)) found += other
            }
        }
        val overlap = found.associate { o -> o.id to (own intersect segments(o.description.lowercase()).flatten().toSet()).size }
        // Only foods that share at least two words ("Spices, bay leaf" is not like "Lettuce, leaf").
        val similar = found.filter { (overlap[it.id] ?: 0) >= minOf(2, own.size) }.sortedByDescending { overlap[it.id] ?: 0 }
        val tests = ArrayList<(LabelUnit) -> Boolean>()
        if (unit != null) tests += { lu -> unit == lu.unit || lu.rest.contains(unit) }
        if (sized) tests += { lu -> Regex("medium|whole|regular").containsMatchIn(lu.rest) }
        if (sized && named != null) tests += { lu -> named.containsMatchIn(lu.rest) }
        val skinless = Regex("skinless|without skin").containsMatchIn(r.description.lowercase())
        // Closest foods first; within equally close foods, the best kind of portion first.
        for (level in similar.map { overlap[it.id] ?: 0 }.distinct().sortedDescending()) {
            val group = similar.filter { (overlap[it.id] ?: 0) == level }
            for (test in tests) {
                for (other in group) {
                    for (portion in other.portions) {
                        val lu = labelUnit(portion.label)
                        if (skinless && Regex("\\bwith skin").containsMatchIn(lu.rest)) continue // 1 thigh without skin, not with
                        if (!isMassOrVolume(lu.unit) && test(lu)) {
                            return Resolved(Portion(portion.label + " (similar food)", portion.grams / lu.amount), true)
                        }
                    }
                }
            }
        }
        if (unit == null) {
            for (other in similar) {
                for (portion in other.portions) {
                    val lu = labelUnit(portion.label)
                    if (!isMassOrVolume(lu.unit)) return Resolved(Portion(portion.label + " (similar food)", portion.grams / lu.amount), true)
                }
            }
        }
        val first = r.portions.firstOrNull()
        if (sized) {
            return if (first != null) Resolved(Portion(first.label, first.grams / labelUnit(first.label).amount), true) else Resolved(Portion("g", 100.0), true)
        }
        // "12 drops" of something with no drop-sized portion: ask instead of guessing.
        return if (first != null) {
            Resolved(Portion(first.label, first.grams / labelUnit(first.label).amount), true, unconverted = true)
        } else {
            Resolved(Portion("g", 1.0), true, unconverted = true)
        }
    }
}
