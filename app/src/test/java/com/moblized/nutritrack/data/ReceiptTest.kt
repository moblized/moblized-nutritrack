package com.moblized.nutritrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class ReceiptTest {

    private val mcdonalds = """
        McDonald's
        Restaurant #12345
        123 Main St
        Springfield, IL 62701
        TEL# 217-555-0100
        KS# 3 10/30/2026 12:04 PM
        Order 47
        1 Big Mac 5.99
        1 M French Fries 3.29
        1 L Coke 2.19
        2 McChicken 5.98
        1 6 McNuggets 4.49
        Sweet N Sour
        Subtotal 21.94
        Tax 1.76
        Take-Out Total 23.70
        Cash Tendered 25.00
        Change 1.30
    """.trimIndent().lines()

    private val grocery = """
        WAL*MART
        GV WHL MILK 007874235145 F 3.12 N
        BANANAS 000000004011KF 1.42 N
        1.82 lb @ 0.78 /lb
        GREEK YOGURT 081803001234 F 5.47 N
        2 @ 0.98
        AVOCADOS 000000004046 1.96 N
        SUBTOTAL 11.97
        VISA ************1234 11.97
    """.trimIndent().lines()

    @Test
    fun readsARestaurantReceipt() {
        val r = ReceiptParser.parse(mcdonalds)
        assertEquals("mcdonald", r.store)
        assertEquals(
            listOf("Big Mac", "M French Fries", "L Coke", "McChicken", "McNuggets"),
            r.items.map { it.text },
        )
        assertEquals(2.0, r.items[3].quantity, 0.0)
        assertEquals(6, r.items[4].pieces)
        assertEquals(1.0, r.items[4].quantity, 0.0)
        for (line in listOf("Subtotal 21.94", "Tax 1.76", "Take-Out Total 23.70", "Cash Tendered 25.00", "Change 1.30", "Sweet N Sour")) {
            assertTrue("should skip $line", line in r.skipped)
        }
    }

    @Test
    fun readsAGroceryReceipt() {
        val r = ReceiptParser.parse(grocery)
        assertNull(r.store)
        assertEquals(listOf("GV WHL MILK", "BANANAS", "GREEK YOGURT", "AVOCADOS"), r.items.map { it.text })
        assertEquals(2.0, r.items[2].quantity, 0.0) // "2 @ 0.98" under it
        assertFalse(r.mexican)
    }

    @Test
    fun readsCountsAndCombos() {
        val r = ReceiptParser.parse(
            listOf(
                "CHIPOTLE MEXICAN GRILL", "Chicken Burrito Bowl 10.95", "White Rice", "Black Beans", "+ Guacamole 2.95",
                "Chips & Guac 5.45", "Spring Rolls (4) 6.95", "Buffalo Wings 12 13.99", "2x Taco 7.00", "Coupon -2.00",
            ),
        )
        assertTrue(r.mexican)
        assertEquals(
            listOf("Chicken Burrito Bowl", "Guacamole", "Chips & Guac", "Spring Rolls", "Buffalo Wings", "Taco"),
            r.items.map { it.text },
        )
        assertEquals(4, r.items[3].pieces)
        assertEquals(12, r.items[4].pieces)
        assertEquals(2.0, r.items[5].quantity, 0.0)
        assertTrue("White Rice" in r.skipped)
    }

    @Test
    fun putsColumnsBackTogether() {
        // Names on the left and prices on the right are read as separate lines, slightly out of level.
        val lines = listOf(
            OcrLine("Big Mac", 40, 400, 260, 440),
            OcrLine("5.99", 900, 404, 990, 444),
            OcrLine("M French Fries", 40, 460, 380, 500),
            OcrLine("3.29", 900, 466, 990, 506),
            OcrLine("Subtotal", 40, 600, 250, 640),
            OcrLine("9.28", 900, 597, 990, 637),
            OcrLine("McDonald's", 300, 100, 700, 150),
        )
        assertEquals(
            listOf("McDonald's", "Big Mac 5.99", "M French Fries 3.29", "Subtotal 9.28"),
            ReceiptParser.rows(lines),
        )
    }

    @Test
    fun matchesRealFoods() {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("usda.db has not been built", db != null)
        val conn = DriverManager.getConnection("jdbc:sqlite:" + db!!.absolutePath)
        val matcher = ReceiptMatcher(JdbcSource(conn))
        // receipt line, store, food name should contain, portion label should contain (or null for any)
        data class Case(val line: String, val store: String?, val food: String, val portion: String?, val mexican: Boolean = false)
        val cases = listOf(
            Case("Big Mac", "mcdonald", "big mac", null),
            Case("M French Fries", "mcdonald", "mcdonald's, french fries", "medium"),
            Case("L Coke", "mcdonald", "cola", "large"),
            Case("McChicken", "mcdonald", "mcchicken", null),
            Case("Caesar Salad", null, "caesar salad", null),
            Case("Breadsticks", null, "breadsticks", null),
            Case("Iced Tea", null, "tea, iced", null),
            Case("Tiramisu", null, "tiramisu", null),
            Case("Chicken Burrito Bowl", null, "burrito bowl, chicken", null, mexican = true),
            Case("Large Drink", null, "soft drink", "large"),
            Case("GV WHL MILK", null, "milk, whole", null),
            Case("BANANAS", null, "banana", null),
            Case("GREEK YOGURT", null, "yogurt, greek", null),
            Case("Grande Latte", null, "latte", "medium"),
            Case("Butter Croissant", null, "croissant", null),
            Case("Tall Cold Brew", null, "iced coffee", "small"),
            Case("Hash Browns", null, "hash brown", null),
            Case("Coffee", null, "coffee", null),
            Case("Pad Thai Chicken", null, "pad thai", null),
            Case("Jasmine Rice", null, "rice, white", null),
            Case("Garlic Knots", null, "garlic bread", null),
            Case("Btl Water", null, "water", null),
            Case("IPA Draft", null, "beer", null),
            Case("Mozzarella Sticks", null, "mozzarella sticks", null),
            Case("House Margarita", null, "margarita", null),
        )
        val failures = ArrayList<String>()
        for (c in cases) {
            val m = matcher.match(ReceiptItem(c.line), c.store, c.mexican).single()
            val item = m.item
            println("RECEIPT ${c.line} -> ${item?.name} [${item?.unitLabel} ${item?.unitGrams}] confident=${m.confident}")
            when {
                item == null -> failures += "${c.line}: no match"
                !item.name.lowercase().contains(c.food) -> failures += "${c.line}: got ${item.name}"
                c.portion != null && !item.unitLabel.lowercase().contains(c.portion) -> failures += "${c.line}: portion ${item.unitLabel}"
                !m.confident -> failures += "${c.line}: not confident"
            }
        }

        // Piece counts become that many pieces.
        matcher.match(ReceiptItem("McNuggets", 1.0, 6), "mcdonald", false).single().item!!.let {
            println("RECEIPT 6 McNuggets -> ${it.name} ${it.quantity} x [${it.unitLabel} ${it.unitGrams}]")
            if (it.quantity != 6.0 || !it.unitLabel.contains("nugget")) failures += "6 McNuggets: ${it.quantity} x ${it.unitLabel}"
        }
        matcher.match(ReceiptItem("Pancakes", 1.0, 3), null, false).single().item!!.let {
            println("RECEIPT 3 Pancakes -> ${it.name} ${it.quantity} x [${it.unitLabel} ${it.unitGrams}]")
            if (it.quantity != 3.0 || !it.unitLabel.contains("pancake")) failures += "3 Pancakes: ${it.quantity} x ${it.unitLabel}"
        }
        // Two foods on one line.
        val combo = matcher.match(ReceiptItem("Chips & Guac"), null, true)
        println("RECEIPT Chips & Guac -> " + combo.joinToString { it.item?.name ?: "-" })
        if (combo.size != 2 || !combo[0].item!!.name.lowercase().contains("tortilla chips") ||
            !combo[1].item!!.name.lowercase().contains("guacamole")
        ) {
            failures += "Chips & Guac: " + combo.joinToString { it.item?.name ?: "-" }
        }
        val burger = matcher.match(ReceiptItem("Cheeseburger w/ Fries"), null, false)
        println("RECEIPT Cheeseburger w/ Fries -> " + burger.joinToString { it.item?.name ?: "-" })
        if (burger.size != 2) failures += "Cheeseburger w/ Fries: " + burger.joinToString { it.item?.name ?: "-" }
        // Things the database can't really match are flagged, not quietly logged.
        val curry = matcher.match(ReceiptItem("Green Curry"), null, false).single()
        println("RECEIPT Green Curry -> ${curry.item?.name} confident=${curry.confident}")
        if (curry.confident) failures += "Green Curry should be a best guess, got ${curry.item?.name}"

        conn.close()
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun prefersTheUsersOwnFood() {
        val mine = Food(FoodSource.CUSTOM, 7, "Chicken Burrito Bowl", "My food", mapOf(Nutrients.ENERGY to 150.0), listOf(Portion("bowl", 650.0)))
        val noUsda = object : FoodCandidateSource {
            override fun candidates(query: String, limit: Int) = emptyList<UsdaDatabase.Candidate>()
            override fun food(id: Long): Food? = null
        }
        val m = ReceiptMatcher(noUsda, listOf(mine)).match(ReceiptItem("CHICKEN BURRITO BOWL", 2.0), null, true).single()
        assertEquals("custom", m.item!!.source)
        assertEquals(1300.0, m.item!!.grams, 1e-9)
        assertTrue(m.confident)
    }
}
