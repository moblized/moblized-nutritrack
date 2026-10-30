package com.moblized.nutritrack.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A line of text the phone read from a receipt photo, and where it sits (pixels; pages stacked top to bottom). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Double get() = (top + bottom) / 2.0
    val height: Int get() = max(1, bottom - top)
    val width: Int get() = max(1, right - left)
}

/** One thing on the receipt: "2 Big Mac" is 2 of them, "6 McNuggets" is one order of 6 pieces. */
data class ReceiptItem(val text: String, val quantity: Double = 1.0, val pieces: Int? = null)

data class ParsedReceipt(
    /** A restaurant chain the food database knows ("mcdonald"), from the receipt's header. */
    val store: String?,
    val items: List<ReceiptItem>,
    /** Lines that aren't food: totals, tax, payment, address... */
    val skipped: List<String>,
    /** Mexican food on the receipt, so "Chips" means tortilla chips. */
    val mexican: Boolean,
)

/** Turns the text of a receipt into the food on it. */
object ReceiptParser {
    private val ic = setOf(RegexOption.IGNORE_CASE)
    private val PRICE = Regex("""^\$?-?\d{1,4}[.,]\d{2}-?$""")
    private val FLAG = Regex("""^[A-Z]{1,2}\*?$|^\*+$""")
    private val TAX_FLAG = Regex("""^[A-Z]\*?$""")
    private val CODE = Regex("""^#?\d{4,}[A-Z]{0,2}$""")
    private val SKIP = Regex(
        """\b(sub\s*-?\s*total|subtotal|total|tax(es)?|vat|hst|gst|pst|tip|gratuity|service charge|change|balance|amount|""" +
            """due|paid|payment|discount|savings?|saved|coupon|promo|rewards?|points|bag fee|deposit|crv|surcharge|fees?|""" +
            """rounding|donation|refund|void|cash|credit|debit|visa|mastercard|master card|amex|american express|discover|ebt|""" +
            """tendered|tend|auth(orization)?|approval|approved|ref|reference|trans(action)?|terminal|merchant|""" +
            """server|cashier|table|guests?|chk|check|ticket|receipt|invoice|thank(s| you)?|visit|survey|www|http|com|""" +
            """tel|phone|items? sold|item count|customer|copy|signature|contactless|entry|tvr|""" +
            """dine in|take ?out|takeout|to go|pick ?up|delivery|drive ?thru|store|cust|register|operator|""" +
            """welcome|hello|hours|manager|mgr|employee|host|seat|cover|card|account|acct|member|loyalty|""" +
            """date|time|station|lane|kiosk|online)\b""",
        ic,
    )
    private val DATE = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\b\d{1,2}:\d{2}\b""")
    private val PHONE = Regex("""\(?\d{3}\)?[-. ]\d{3}[-. ]\d{4}""")
    private val MASKED = Regex("""[*xX#]{4,}\s*\d{2,4}""")
    private val ADDRESS = Regex(
        """^\d+\s+\w+.*\b(st|street|ave|avenue|blvd|boulevard|rd|road|dr|drive|ln|lane|hwy|highway|pkwy|parkway|way|ct|court|pl|place|suite|ste|plaza)\b""",
        ic,
    )
    private val CITY = Regex("""[A-Za-z .]+,?\s+[A-Z]{2}\s+\d{5}""")
    private val WEIGHT = Regex("""\d+([.,]\d+)?\s*(lb|lbs|kg)\b""", ic)
    private val AT_QTY = Regex("""(\d{1,2})\s*@\s*\$?\d{1,4}[.,]\d{2}(\s*(ea|each|/ea))?""", ic)
    private const val COUNTABLE =
        "(?:mc)?nuggets?|nugs?|wings?|tenders?|strips?|pieces?|pcs?|ct|count|rolls?|tacos?|pancakes?|eggs?|sliders?|" +
            "dumplings?|potstickers?|knots?|breadsticks?|sticks?|shrimp|bites?|slices?|cookies?|donuts?|doughnuts?"
    private val MODIFIER = Regex("""^\s*(\+|add\b|extra\b|xtra\b)""", ic)
    private val NEGATIVE = Regex("""^\s*(-|no\b|hold\b|without\b|w/o\b|light\b|easy\b|lite\b|sub\b|on the side\b)""", ic)
    private val LEAD_QTY = Regex("""^(\d{1,2})\s*[xX]?\s+(?=(\d{1,3}\s*(pc|pcs|piece|ct)?\s+)?\S*[A-Za-z])""")
    private val TRAIL_QTY = Regex("""\s+[xX]\s*(\d{1,2})$|\bqty:?\s*(\d{1,2})\b""", ic)
    private val LEAD_PIECES = Regex("""^(\d{1,3})\s*(pc|pcs|piece|ct)?\s+(?=($COUNTABLE)\b)""", ic)
    private val PAREN_PIECES = Regex("""\((\d{1,3})\)|\b(\d{1,3})\s*(pc|pcs|piece|ct)\b""", ic)
    private val TRAIL_PIECES = Regex("""\b($COUNTABLE)\s+(\d{1,3})$""", ic)
    private val MEXICAN = Regex("burrito|taco|quesadilla|guac|salsa|queso|nacho|enchilada|fajita", ic)

    /** Restaurant chains the food database has dishes for, as they appear on a receipt. */
    private val STORES = listOf(
        "mcdonald" to "mcdonald", "burger king" to "burgerking", "wendy" to "wendy", "taco bell" to "tacobell",
        "kfc" to "kfc", "kentucky fried" to "kfc", "subway" to "subway", "pizza hut" to "pizzahut", "denny" to "denny",
        "applebee" to "applebee", "olive garden" to "olivegarden", "chick-fil-a" to "chickfila", "chick fil a" to "chickfila",
        "popeye" to "popeyes", "arby" to "arby", "cracker barrel" to "crackerbarrel", "friday" to "friday",
        "on the border" to "ontheborder", "carrabba" to "carrabba",
    )

    private fun letters(s: String) = s.count { it.isLetter() }

    /** Puts words printed on the same row back together ("Big Mac" on the left, "5.99" on the right). */
    fun rows(lines: List<OcrLine>): List<String> {
        val ls = lines.filter { it.text.isNotBlank() }
        if (ls.isEmpty()) return emptyList()
        val h = ls.map { it.height }.sorted()[ls.size / 2].toDouble()
        val rows = ArrayList<MutableList<OcrLine>>()
        for (l in ls.sortedBy { it.centerY }) {
            val row = rows.filter { r ->
                abs(r.map { it.centerY }.average() - l.centerY) <= 0.6 * h &&
                    r.none { o -> overlapX(o, l) > 0.3 * min(o.width, l.width) }
            }.minByOrNull { r -> abs(r.map { it.centerY }.average() - l.centerY) }
            if (row != null) row += l else rows += mutableListOf(l)
        }
        return rows.sortedBy { r -> r.map { it.centerY }.average() }
            .map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    private fun overlapX(a: OcrLine, b: OcrLine) = max(0, min(a.right, b.right) - max(a.left, b.left))

    private data class Row(val raw: String, val text: String, val price: String?)

    fun parse(rows: List<String>): ParsedReceipt {
        val lines = rows.map { it.split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ") }.filter { it.isNotEmpty() }
        var store: String? = null
        for (r in lines.take(8)) {
            val low = r.lowercase().replace("'", "").replace("’", "")
            store = STORES.firstOrNull { low.contains(it.first) }?.second
            if (store != null) break
        }
        val cleaned = lines.map { r ->
            var toks = r.replace("$ ", "$").split(' ')
            var price: String? = null
            var i = toks.size - 1
            while (i > 0 && FLAG.matches(toks[i]) && !PRICE.matches(toks[i])) i--
            if (i >= 0 && PRICE.matches(toks[i])) {
                price = toks[i]
                // A tax flag just before the price ("F 3.12"); two letters stay ("LG" is a size).
                toks = toks.subList(0, i).toMutableList().also { t -> if (t.size > 1 && TAX_FLAG.matches(t.last())) t.removeAt(t.size - 1) }
            }
            Row(r, toks.filterNot { CODE.matches(it) }.joinToString(" ").trim(' ', '.', ':', '*'), price)
        }
        val priced = cleaned.count { it.price != null && letters(it.text) >= 3 }
        val items = ArrayList<ReceiptItem>()
        val skipped = ArrayList<String>()
        for (row in cleaned) {
            val text = row.text
            val low = text.lowercase()
            val standalone = AT_QTY.find(row.raw)
            if (standalone != null && letters(AT_QTY.replace(row.raw, "")) < 3) {
                // "2 @ 0.98" under an item: how many of it
                if (items.isNotEmpty()) items[items.size - 1] = items.last().copy(quantity = standalone.groupValues[1].toDouble())
                continue
            }
            val at = AT_QTY.find(text)
            val notFood = letters(text) < 3 || SKIP.containsMatchIn(low) || DATE.containsMatchIn(text) ||
                PHONE.containsMatchIn(text) || MASKED.containsMatchIn(text) || ADDRESS.containsMatchIn(text) ||
                CITY.containsMatchIn(text) || WEIGHT.containsMatchIn(text) || row.price?.contains('-') == true
            val modifier = MODIFIER.containsMatchIn(low)
            if (notFood || NEGATIVE.containsMatchIn(low) || (priced >= 2 && row.price == null && !modifier) ||
                (store != null && row.raw == lines.first() && row.price == null)
            ) {
                skipped += row.raw
                continue
            }
            var qty = 1.0
            var t = text
            if (at != null) {
                qty = at.groupValues[1].toDouble()
                t = AT_QTY.replace(t, " ")
            }
            t = t.replace(Regex("""^[\s+*>.-]+"""), "").replace(Regex("""^(add|extra|xtra)\s+""", ic), "")
            LEAD_QTY.find(t)?.let { m ->
                val n = m.groupValues[1].toInt()
                if (n in 1..30) {
                    qty = n.toDouble()
                    t = t.substring(m.range.last + 1)
                }
            }
            TRAIL_QTY.find(t)?.let { m ->
                qty = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toDouble()
                t = (t.substring(0, m.range.first) + t.substring(m.range.last + 1)).trim()
            }
            // Piece counts: "6 McNuggets", "Spring Rolls (4)", "Wings 12", "8pc"
            var pieces: Int? = null
            LEAD_PIECES.find(t)?.let { m ->
                pieces = m.groupValues[1].toInt()
                t = t.substring(m.range.last + 1)
            }
            if (pieces == null) {
                PAREN_PIECES.find(t)?.let { m ->
                    pieces = m.groupValues[1].ifEmpty { m.groupValues[2] }.toInt()
                    t = (t.substring(0, m.range.first) + " " + t.substring(m.range.last + 1)).trim()
                }
            }
            if (pieces == null) {
                TRAIL_PIECES.find(t)?.let { m ->
                    pieces = m.groupValues[2].toInt()
                    t = t.substring(0, m.range.first) + m.groupValues[1]
                }
            }
            if (pieces?.let { it in 1..100 } != true) pieces = null
            t = t.split(' ').filter(String::isNotBlank).joinToString(" ")
            if (letters(t) < 3) {
                skipped += row.raw
                continue
            }
            items += ReceiptItem(t, qty, pieces)
        }
        // The same thing rung up twice is one row with a bigger quantity.
        val merged = ArrayList<ReceiptItem>()
        for (it in items) {
            val i = merged.indexOfFirst { o -> o.text.equals(it.text, ignoreCase = true) && o.pieces == it.pieces }
            if (i >= 0) merged[i] = merged[i].copy(quantity = merged[i].quantity + it.quantity) else merged += it
        }
        return ParsedReceipt(store, merged, skipped, lines.any { MEXICAN.containsMatchIn(it) })
    }
}

/** What a receipt line was matched to. */
data class ReceiptMatch(
    /** The receipt text this row came from ("Chips & Guac" can give two rows). */
    val text: String,
    val item: RecipeItem?,
    /** Every word of the line was found in the food's name; otherwise it's a best guess to check. */
    val confident: Boolean,
    /** The portion size is a guess (no serving size for this food). */
    val estimatedAmount: Boolean = false,
)

/**
 * Matches restaurant and grocery receipt lines to foods: the user's own foods first, then USDA dishes,
 * at a typical serving (or the size and piece count on the receipt).
 */
class ReceiptMatcher(private val usda: FoodCandidateSource, private val personal: List<Food> = emptyList()) {

    private enum class Kind { NOUN, DESC, WITH }

    private data class Terms(val terms: List<String>, val kinds: Map<String, Kind>, val size: String?)

    fun match(item: ReceiptItem, store: String?, mexican: Boolean): List<ReceiptMatch> =
        match(item.text, item.quantity, item.pieces, store, mexican, split = true)

    private fun match(text: String, qty: Double, pieces: Int?, store: String?, mexican: Boolean, split: Boolean): List<ReceiptMatch> {
        val t = terms(text, mexican)
        if (t.terms.isEmpty()) return listOf(ReceiptMatch(text, null, false))
        personalMatch(text, t, qty)?.let { return listOf(it) }
        val nouns = t.terms.filter { t.kinds[it] == Kind.NOUN }.ifEmpty { t.terms.filter { t.kinds[it] != Kind.WITH } }.ifEmpty { t.terms }
        var head = nouns.last()
        val main = t.terms.filter { t.kinds[it] != Kind.WITH }.ifEmpty { t.terms }
        val searches = ArrayList<String>()
        searches += t.terms.joinToString(" ")
        searches += main.joinToString(" ")
        if (main.size > 2) searches += main.takeLast(2).joinToString(" ")
        main.zipWithNext().forEach { (a, b) -> searches += "$a $b" }
        searches += main.take(5)
        val seen = HashSet<Long>()
        val cands = ArrayList<UsdaDatabase.Candidate>()
        for (s in searches.distinct()) {
            for (c in runCatching { usda.candidates(s, 60) }.getOrDefault(emptyList())) {
                if (seen.add(c.id)) cands += c
            }
        }
        var covered = cands.filter { head in analyse(it.description).words }
        if (covered.isEmpty()) {
            for (h in nouns.dropLast(1).reversed()) {
                covered = cands.filter { h in analyse(it.description).words }
                if (covered.isNotEmpty()) {
                    head = h
                    break
                }
            }
        }
        val best = covered.maxByOrNull { score(it, t, head, store) }
        val conf = best?.let { coverage(analyse(it.description).words, t, head) } ?: 0.0
        // "Cheeseburger w/ Fries", "Chips & Guac": two foods on one line.
        if (split && conf < 0.99 && SPLIT_HINT.containsMatchIn(" ${text.lowercase()} ")) {
            val parts = text.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) {
                val dips = parts.any { Regex("guac|salsa|queso", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                val subs = parts.map { match(it, qty, null, store, mexican || dips, split = false) }
                if (subs.all { s -> s.size == 1 && s[0].item != null && s[0].confident }) return subs.map { it[0] }
            }
        }
        if (best == null) return listOf(ReceiptMatch(text, null, false))
        val food = runCatching { usda.food(best.id) }.getOrNull() ?: return listOf(ReceiptMatch(text, null, false))
        val count = pieces ?: if (qty > 1) qty.toInt() else null
        val (portion, estimated) = choosePortion(best, head, t.size, count)
        val quantity = if (pieces != null) qty * pieces else qty
        val item = RecipeItem(
            recipeId = 0,
            source = FoodSource.USDA.key,
            refId = food.id,
            name = food.name,
            quantity = quantity,
            unitLabel = portion.label,
            unitGrams = portion.grams,
            grams = quantity * portion.grams,
            per100 = NutrientCodec.encode(food.per100g),
        )
        return listOf(ReceiptMatch(text, item, conf >= CONFIDENT, estimated))
    }

    /** A food or recipe the user saved under the same name, e.g. their usual order. */
    private fun personalMatch(text: String, t: Terms, qty: Double): ReceiptMatch? {
        val main = t.terms.filter { t.kinds[it] != Kind.WITH }
        if (main.isEmpty()) return null
        val wanted = main.toSet()
        val f = personal.firstOrNull { p ->
            words(p.name).map(::singular).filter { it !in DROP && it !in FILLER }.toSet() == wanted
        } ?: return null
        val portion = f.defaultUnit()
        val quantity = if (f.portions.isEmpty()) 100.0 * qty else qty
        return ReceiptMatch(
            text,
            RecipeItem(
                recipeId = 0, source = f.source.key, refId = f.id, name = f.name, quantity = quantity,
                unitLabel = portion.label, unitGrams = portion.grams, grams = quantity * portion.grams,
                per100 = NutrientCodec.encode(f.per100g),
            ),
            confident = true,
        )
    }

    // ---------- reading the receipt line ----------

    private fun terms(text: String, mexican: Boolean): Terms {
        var low = " " + text.lowercase().replace('’', '\'').replace("w/", " with ") + " "
        if (low.isBlank()) return Terms(emptyList(), emptyMap(), null)
        if (Regex("""^\s*chips\s*$""").matches(low)) low = if (mexican) " tortilla chips " else " potato chips "
        low = Regex("""(\w)'s\b""").replace(low) { it.groupValues[1] + "s" }
        for ((rx, rep) in PHRASES) low = rx.replace(low, rep)
        val ws = low.split(Regex("[^a-z0-9']+")).map { it.trim('\'') }.filter { it.isNotEmpty() }
        var size: String? = null
        var after = false
        val out = ArrayList<Pair<String, Boolean>>()
        ws.forEachIndexed { i, w ->
            if (w == "with" || w == "w") {
                after = true
                return@forEachIndexed
            }
            val s = SIZES[w]
            if (s != null && (w.length > 1 || (i == 0 && ws.size > 1))) {
                if (size == null) size = s
                return@forEachIndexed
            }
            if (w.first().isDigit()) return@forEachIndexed // 2l, 12oz, 16
            val rep = ABBR[w]
            val parts = rep?.split(' ') ?: if (w !in DROP && w.length > 1) listOf(w) else emptyList()
            parts.forEach { out += it to after }
        }
        val terms = ArrayList<String>()
        val kinds = HashMap<String, Kind>()
        for ((w0, aft) in out) {
            val w = singular(w0)
            if (w !in terms) {
                terms += w
                kinds[w] = when {
                    aft -> Kind.WITH
                    w in DESCRIPTORS -> Kind.DESC
                    else -> Kind.NOUN
                }
            }
        }
        return Terms(terms, kinds, size)
    }

    // ---------- choosing the food ----------

    private class Desc(val text: String, val segs: List<List<String>>, val words: Set<String>, val brand: String?)

    private val descCache = HashMap<String, Desc>()

    private fun analyse(d: String): Desc = descCache.getOrPut(d) {
        val dl = INCLUDES.replace(d, "").lowercase().replace("'", "").replace("\"", "")
        val segs = dl.split(',').map { s -> words(s).map(::singular) }.filter { it.isNotEmpty() }
        val key = dl.filter { it.isLetter() }
        Desc(dl, segs, segs.flatten().toSet(), BRANDS.firstOrNull { key.contains(it) })
    }

    private fun weight(t: String, head: String, terms: Terms) = when {
        t == head -> 1.6
        terms.kinds[t] == Kind.DESC -> 0.5
        terms.kinds[t] == Kind.WITH -> 0.6
        else -> 1.0
    }

    private fun coverage(words: Set<String>, t: Terms, head: String): Double {
        val total = t.terms.sumOf { weight(it, head, t) }
        return t.terms.filter { it in words }.sumOf { weight(it, head, t) } / total
    }

    private fun score(c: UsdaDatabase.Candidate, t: Terms, head: String, store: String?): Double {
        val d = analyse(c.description)
        val terms = t.terms.toSet()
        val brand = d.brand
        fun isBrandWord(w: String) = brand != null && w.length > 1 && brand.contains(w.filter { it.isLetter() })
        var sc = 6 * coverage(d.words, t, head)
        var li = 0
        var lead = d.segs.firstOrNull() ?: emptyList()
        if (lead.isNotEmpty() && lead[0] in setOf("fast", "beverage", "alcoholic", "restaurant") && d.segs.size > 1) {
            lead = d.segs[1]
            li = 1
        }
        fun extra(seg: List<String>) = seg.filter { it !in terms && it !in FILLER && !isBrandWord(it) && it !in DEFAULTS }
        val leadExtra = extra(lead)
        if (leadExtra.isEmpty()) sc += 1.0
        sc -= 0.35 * leadExtra.size
        val rest = d.segs.drop(li + 1)
        rest.getOrNull(0)?.let { sc -= 0.3 * extra(it).size }
        rest.getOrNull(1)?.let { sc -= 0.15 * extra(it).size }
        // "Egg roll" for egg roll, not "Roll, egg bread"
        val nouns = t.terms.filter { t.kinds[it] != Kind.WITH }
        if (nouns.size > 1 && (" " + d.segs.flatten().joinToString(" ") + " ").contains(" " + nouns.joinToString(" ") + " ")) sc += 0.8
        // What a description says the food is NOT ("no dressing") doesn't count against it.
        val neg = NEGATED.findAll(d.text).map { singular(it.groupValues[2]) }.toSet()
        for (seg in d.text.split(',')) {
            val m = WITH.find(seg) ?: continue
            val extras = words(m.groupValues[1]).count { w ->
                singular(w) !in terms && w !in FILLER && singular(w) !in neg && w != "no" && w != "without"
            }
            sc -= 0.3 * min(3, extras)
        }
        sc -= 1.2 * d.words.count { it in BAD && it !in terms && it !in neg }
        sc += 0.3 * d.words.count { it in DEFAULTS && it !in terms }
        if ("raw" in d.words) sc += 0.2
        if ("restaurant" in d.words || "fast" in d.words) sc += 0.3
        if (brand != null) {
            if (store != null && brand.contains(store)) {
                sc += 2.5
            } else if (terms.none { isBrandWord(it) }) {
                sc -= 1.2 // a chain's version when the receipt isn't from that chain
            }
        }
        if (c.source == 2) sc += 0.2
        sc -= d.text.length / 80.0
        return sc
    }

    // ---------- choosing the amount ----------

    /** "2 eggs, 96 g" is "1 egg, 48 g". */
    private fun perOne(p: Portion): Portion {
        val m = PER_ONE.find(p.label) ?: return p
        val n = m.groupValues[1].toInt()
        return if (n > 1) Portion("1 " + singular(m.groupValues[2].lowercase()) + m.groupValues[3], p.grams / n) else p
    }

    private fun choosePortion(c: UsdaDatabase.Candidate, head: String, size: String?, pieces: Int?): Pair<Portion, Boolean> {
        val ps = c.portions.filter { it.grams > 0 }.map(::perOne)
        fun has(p: Portion, w: String) = Regex("\\b" + Regex.escape(w) + "s?\\b").containsMatchIn(p.label.lowercase())
        fun low(p: Portion) = p.label.lowercase()
        val cnt = ps.filter { !MEASURE.containsMatchIn(it.label) }
        if (size != null) {
            for (a in SIZE_WORDS.getValue(size)) {
                val hits = cnt.filter { Regex("\\b$a\\b").containsMatchIn(low(it)) && "extra" !in low(it) }
                val whole = hits.filter { !Regex("piece|slice").containsMatchIn(low(it)) }
                (whole.firstOrNull() ?: hits.firstOrNull())?.let { return it to false }
            }
        }
        if (pieces != null) {
            cnt.filter { has(it, head) && !TINY.containsMatchIn(low(it)) }
                .sortedBy { if (Regex("medium|regular|any size").containsMatchIn(low(it))) 0 else 1 }
                .firstOrNull()?.let { return it to false }
            cnt.firstOrNull { Regex("\\b(piece|each|item|drummette|wingette|nugget|tender|strip)\\b").containsMatchIn(low(it)) }
                ?.let { return it to false }
        }
        ps.firstOrNull { it.label == "typical serving" }?.let { return it to false }
        val order = listOf<(Portion) -> Boolean>(
            { has(it, head) && Regex("medium|regular").containsMatchIn(low(it)) },
            { Regex("\\b(medium|regular)\\b").containsMatchIn(low(it)) && !Regex("piece|slice").containsMatchIn(low(it)) },
            { has(it, head) && !TINY.containsMatchIn(low(it)) },
            { Regex("serving|order|item|each|sandwich").containsMatchIn(low(it)) },
            { !TINY.containsMatchIn(low(it)) },
        )
        for (f in order) cnt.firstOrNull(f)?.let { return it to false }
        if (DRINK.containsMatchIn(c.description.lowercase())) {
            ps.firstOrNull { it.label.startsWith("1 fl oz") }?.let { return Portion("12 fl oz", 12 * it.grams) to true }
            return Portion("12 fl oz", 355.0) to true
        }
        ps.firstOrNull()?.let { return it to true }
        return Portion("serving", 100.0) to true
    }

    companion object {
        /** Share of the line's words found in the food's name to tick it without asking. */
        const val CONFIDENT = 0.75

        private val INCLUDES = Regex("""\s*\((?:includes|Includes)[^)]*\)""")
        private val NEGATED = Regex("""\b(no|without)\s+(\w+)""")
        private val WITH = Regex("""\bwith\b(.*)""")
        private val SPLIT_HINT = Regex("""\s(&|and|w/|with|\+|n)\s""")
        private val SPLIT = Regex("""\s+(?:&|and|w/|with|\+|n)\s+|\s*w/\s*""", RegexOption.IGNORE_CASE)
        private val PER_ONE = Regex("""^\s*(\d+)\s+(\w+)(.*)$""")
        private val MEASURE = Regex(
            """^\s*[\d/. ]*\s*(oz|fl oz|g|lb|cup|cups|tbsp|tablespoon|tsp|teaspoon|ml|fl|surface|cubic|inch)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val TINY = Regex("mini|miniature|bite|silver dollar|snack|kid|child|toaster")
        private val DRINK = Regex("""beverage|beer|soda|drink|juice|coffee|tea\b|milk|water|lemonade|wine|cola""")
        private val SIZE_WORDS = mapOf(
            "small" to listOf("small", "kids?", "child"),
            "medium" to listOf("medium", "regular", "small/regular"),
            "large" to listOf("large"),
        )

        private fun words(s: String): List<String> =
            s.lowercase().replace("'", "").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

        private val KEEP = setOf("fries", "nachos", "hummus", "couscous", "asparagus", "swiss", "molasses", "chips")

        internal fun singular(w: String): String = when {
            w in KEEP || w.length <= 3 -> w
            w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
            w.endsWith("ches") || w.endsWith("shes") || w.endsWith("sses") || w.endsWith("xes") || w.endsWith("oes") -> w.dropLast(2)
            w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
            else -> w
        }

        private val SIZES = mapOf(
            "s" to "small", "sm" to "small", "sml" to "small", "small" to "small", "tall" to "small", "kid" to "small",
            "kids" to "small", "jr" to "small", "junior" to "small", "child" to "small", "mini" to "small",
            "m" to "medium", "md" to "medium", "med" to "medium", "mdm" to "medium", "medium" to "medium",
            "grande" to "medium", "reg" to "medium", "regular" to "medium", "rg" to "medium",
            "l" to "large", "lg" to "large", "lrg" to "large", "lge" to "large", "large" to "large", "venti" to "large",
            "xl" to "large",
        )

        /** Receipt shorthand. */
        private val ABBR = mapOf(
            "chkn" to "chicken", "chk" to "chicken", "chix" to "chicken", "ckn" to "chicken",
            "brgr" to "burger", "bgr" to "burger", "burg" to "burger", "chz" to "cheese", "chs" to "cheese", "chse" to "cheese",
            "chzbgr" to "cheeseburger", "chzbrgr" to "cheeseburger", "chsbrgr" to "cheeseburger",
            "hmbgr" to "hamburger", "hamb" to "hamburger",
            "sndwch" to "sandwich", "sndw" to "sandwich", "swch" to "sandwich", "sandw" to "sandwich", "sw" to "sandwich",
            "frs" to "fries", "fry" to "fries", "ff" to "french fries", "fries" to "french fries",
            "drk" to "drink", "drnk" to "drink", "bev" to "drink",
            "wht" to "white", "whl" to "whole", "ww" to "whole wheat", "brd" to "bread", "bgl" to "bagel", "crm" to "cream",
            "ckie" to "cookie", "choc" to "chocolate", "chc" to "chocolate", "van" to "vanilla", "vnla" to "vanilla",
            "strwb" to "strawberry", "strwbry" to "strawberry", "blubry" to "blueberry", "blueb" to "blueberry",
            "bnna" to "banana", "bnn" to "banana", "appl" to "apple", "orng" to "orange", "oj" to "orange juice",
            "pb" to "peanut butter", "bf" to "beef", "grnd" to "ground", "grd" to "ground", "tky" to "turkey",
            "trky" to "turkey", "prk" to "pork", "bcn" to "bacon", "saus" to "sausage", "ssg" to "sausage",
            "pep" to "pepperoni", "pepp" to "pepperoni", "mozz" to "mozzarella", "ched" to "cheddar", "chdr" to "cheddar",
            "parm" to "parmesan", "ltc" to "lettuce", "tom" to "tomato", "brocc" to "broccoli", "spnch" to "spinach",
            "avo" to "avocado", "guac" to "guacamole", "quesa" to "quesadilla", "qsd" to "quesadilla", "burr" to "burrito",
            "ench" to "enchilada", "wng" to "wing", "wngs" to "wings", "tndr" to "tender", "tndrs" to "tenders",
            "nug" to "nugget", "nugs" to "nuggets", "mcnugget" to "chicken nugget", "mcnuggets" to "chicken nuggets",
            "shk" to "shake", "mlkshk" to "milkshake", "mlk" to "milk", "yog" to "yogurt", "ygrt" to "yogurt",
            "grk" to "greek", "cof" to "coffee", "cfe" to "coffee", "coff" to "coffee", "capp" to "cappuccino",
            "espr" to "espresso", "spag" to "spaghetti", "sld" to "salad", "caes" to "caesar", "rnch" to "ranch",
            "bbq" to "barbecue", "donut" to "doughnut", "donuts" to "doughnuts", "muff" to "muffin", "crois" to "croissant",
            "waff" to "waffle", "bisc" to "biscuit", "grvy" to "gravy",
            "coke" to "soft drink cola", "pepsi" to "soft drink cola", "cola" to "soft drink cola", "soda" to "soft drink",
            "pop" to "soft drink", "sprite" to "soft drink lemon lime", "h2o" to "water", "lmnd" to "lemonade",
            "ipa" to "beer", "lager" to "beer", "ale" to "beer", "pilsner" to "beer", "stout" to "beer",
        )

        private val PHRASES = listOf(
            Regex("""\bmac\s*(n|&|and)\s*(chs|cheese)\b""") to " macaroni and cheese ",
            Regex("""\bhash\s*br(own)?s?\b|\bhashbrowns?\b""") to " hash brown ",
            Regex("""\bfr(ench|nch)?\.?\s*toast\b""") to " french toast ",
            Regex("""\bhot\s*dog\b|\bhtdg\b""") to " hot dog ",
            Regex("""\bfountain\s*(drink|soda)?\b|\bsoft\s*drink\b""") to " soft drink ",
            Regex("""\bdiet\s*(coke|pepsi|cola)\b""") to " soft drink cola diet ",
            Regex("""\bdr\.?\s*pepper\b""") to " soft drink pepper type ",
            Regex("""\bthai\s*iced?\s*tea\b""") to " tea iced sweetened ",
            Regex("""\b(iced?|sweet|swt)\s*tea\b""") to " tea iced ",
            Regex("""\bcold\s*brew\b""") to " iced coffee ",
            Regex("""\bbud\s*l(ight|t)\b""") to " bud light ",
            Regex("""\bspring\s*rolls?\b""") to " egg roll ",
            Regex("""\bgarlic\s*knots?\b""") to " garlic bread ",
            Regex("""\b(jasmine|steamed|white)\s*rice\b""") to " rice white cooked ",
            Regex("""\bbuffalo\s*wings?\b""") to " hot sauce chicken wings ",
            Regex("""\bbig\s*mac\b""") to " big mac ",
        )

        private val DROP = setOf(
            "org", "organic", "gv", "ks", "kirkland", "great", "value", "fresh", "the", "of", "a", "an", "ea", "each",
            "pc", "pcs", "piece", "pieces", "ct", "count", "pk", "pack", "and", "n", "combo", "meal", "classic",
            "original", "orig", "famous", "house", "homemade", "new", "special", "side", "add", "extra", "x", "qty", "item",
            "lb", "lbs", "oz", "fl", "gal", "gallon", "ltr", "liter", "dz", "doz", "btl", "bottle", "bottled", "can", "draft",
            "draught", "pint", "glass", "cup", "order", "ord", "app", "appetizer", "entree", "dessert", "our", "signature",
            "premium", "deluxe", "supreme", "ultimate", "style", "made", "scratch", "daily", "kid", "kids",
        )
        private val DESCRIPTORS = setOf(
            "hot", "scrambled", "fried", "grilled", "baked", "roasted", "steamed", "crispy", "crunchy", "spicy", "sweet",
            "sour", "iced", "frozen", "toasted", "smoked", "boiled", "poached", "sauteed", "blackened", "breaded", "plain",
            "diet", "light", "double", "triple", "single", "boneless", "cooked", "sweetened", "unsweetened", "white",
            "whole", "large", "small", "medium",
        )
        /** Chains in the food database, as letters only ("McDONALD'S" -> "mcdonalds"). */
        private val BRANDS = listOf(
            "mcdonald", "burgerking", "wendys", "wendy", "tacobell", "kfc", "subway", "pizzahut", "denny", "applebee",
            "olivegarden", "chickfila", "popeyes", "arby", "crackerbarrel", "friday", "ontheborder", "carrabba",
        )
        /** Versions nobody orders by default (dressing for a salad, a mix, baby food). */
        private val BAD = setOf(
            "dressing", "mix", "dry", "powder", "powdered", "frozen", "unprepared", "babyfood", "baby", "toddler",
            "concentrate", "syrup", "imitation", "meatless", "decaffeinated", "diet", "instant", "canned", "school", "kit",
            "filling", "topping", "seasoning", "vegetarian", "vegan", "substitute", "dehydrated", "dried", "puree",
            "toaster", "turkey", "sandwich", "mini", "miniature", "bits", "bit", "strip", "stuffed", "sauce", "spread",
            "dip", "soup", "juice", "light", "lowfat", "nonfat", "reduced", "fat-free", "skim", "flavored", "glutinous",
            "recipe", "home", "salad",
        )
        private val FILLER = setOf(
            "nfs", "from", "fast", "food", "foods", "restaurant", "with", "and", "or", "in", "on", "any", "type", "regular",
            "ns", "as", "to", "a", "for", "other", "source", "sources", "family", "style", "commercially", "prepared",
        )
        private val DEFAULTS = setOf("plain", "nfs", "regular", "original", "white", "whole", "lowfat", "brewed")
    }
}
