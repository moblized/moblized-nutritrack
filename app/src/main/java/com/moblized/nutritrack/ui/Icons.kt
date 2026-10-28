package com.moblized.nutritrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients as N

/** Emoji for foods, meals and nutrients. */
object Emoji {

    /**
     * Keyword → emoji. A food's description is scanned word by word from the start, so
     * "Soup, chicken broth" is a soup and "Chicken, breast" is chicken.
     */
    private val keywords: Map<String, String> = buildMap {
        fun add(emoji: String, words: String) = words.split(' ').forEach { put(it, emoji) }
        add("🍌", "banana")
        add("🍎", "apple applesauce")
        add("🍐", "pear")
        add("🍊", "orange tangerine clementine mandarin")
        add("🍋", "lemon lime")
        add("🍇", "grape raisin")
        add("🍓", "strawberry raspberry")
        add("🫐", "blueberry blackberry cranberry")
        add("🍒", "cherry")
        add("🍑", "peach nectarine apricot")
        add("🍍", "pineapple")
        add("🥭", "mango papaya")
        add("🍉", "watermelon")
        add("🍈", "melon cantaloupe honeydew")
        add("🥥", "coconut")
        add("🥝", "kiwi kiwifruit")
        add("🥑", "avocado guacamole")
        add("🥦", "broccoli cauliflower")
        add("🥕", "carrot")
        add("🍅", "tomato salsa ketchup")
        add("🥔", "potato hashbrown")
        add("🍠", "yam")
        add("🌽", "corn cornmeal polenta grits")
        add("🫑", "bell pepper")
        add("🌶️", "chili jalapeno chile habanero serrano")
        add("🧅", "onion shallot scallion leek")
        add("🧄", "garlic")
        add("🍄", "mushroom")
        add("🥬", "lettuce spinach kale cabbage chard collard arugula greens bok")
        add("🥒", "cucumber pickle zucchini squash")
        add("🍆", "eggplant")
        add("🫛", "pea peas edamame")
        add("🫘", "bean beans lentil lentils chickpea chickpeas hummus legume")
        add("🥜", "peanut")
        add("🌰", "almond walnut pecan cashew pistachio hazelnut nut nuts chestnut")
        add("🌻", "sunflower seed seeds flaxseed chia")
        add("🎃", "pumpkin")
        add("🥚", "egg eggs omelet")
        add("🧀", "cheese parmesan mozzarella cheddar ricotta feta")
        add("🥛", "milk yogurt kefir cream buttermilk")
        add("🧈", "butter margarine ghee")
        add("🫒", "oil olive olives")
        add("🍗", "chicken poultry turkey duck")
        add("🥩", "beef steak veal lamb venison bison")
        add("🍖", "pork ribs game")
        add("🥓", "bacon ham")
        add("🌭", "sausage frankfurter hotdog bratwurst salami pepperoni bologna")
        add("🐟", "fish salmon tuna cod tilapia trout sardine anchovy halibut mackerel pollock catfish")
        add("🦐", "shrimp prawn")
        add("🦀", "crab lobster crayfish")
        add("🦪", "oyster clam mussel scallop")
        add("🍞", "bread toast bun roll biscuit")
        add("🥐", "croissant pastry danish")
        add("🥯", "bagel")
        add("🥨", "pretzel")
        add("🫓", "tortilla pita naan flatbread")
        add("🥞", "pancake crepe")
        add("🧇", "waffle")
        add("🌾", "wheat flour barley rye bran quinoa couscous bulgur millet")
        add("🥣", "cereal oat oats oatmeal granola muesli porridge")
        add("🍚", "rice")
        add("🍝", "spaghetti pasta macaroni lasagna noodle penne fettuccine linguine")
        add("🍜", "ramen soba udon pho")
        add("🥟", "dumpling gyoza wonton pierogi")
        add("🍣", "sushi")
        add("🍕", "pizza")
        add("🍔", "hamburger burger cheeseburger")
        add("🌮", "taco tostada")
        add("🌯", "burrito wrap enchilada")
        add("🥪", "sandwich sub")
        add("🍟", "fries")
        add("🍿", "popcorn")
        add("🥗", "salad")
        add("🍲", "soup stew broth bouillon curry casserole")
        add("🥫", "sauce gravy")
        add("🍯", "honey syrup molasses")
        add("🍫", "chocolate cocoa")
        add("🍬", "candy sugar sugars sweet sweets")
        add("🍪", "cookie cookies cracker crackers")
        add("🍰", "cake cheesecake")
        add("🧁", "cupcake muffin")
        add("🍩", "doughnut donut")
        add("🍨", "ice")
        add("🥧", "pie")
        add("☕", "coffee espresso latte cappuccino")
        add("🍵", "tea")
        add("🧃", "juice")
        add("🥤", "soda cola beverage beverages drink smoothie shake")
        add("🍺", "beer")
        add("🍷", "wine")
        add("🍸", "liquor vodka whiskey rum gin")
        add("💧", "water")
        add("🧂", "salt spices spice seasoning")
        add("🌿", "basil oregano thyme rosemary parsley cilantro mint dill herb herbs")
    }

    /** Two-word names that mean something different from their first word. */
    private val pairs: Map<String, String> = mapOf(
        "cream cheese" to "🧀", "ice cream" to "🍨", "hot dog" to "🌭", "sweet potato" to "🍠",
        "peanut butter" to "🥜", "green bean" to "🫛", "french fry" to "🍟", "french fries" to "🍟",
        "cherry tomato" to "🍅", "egg white" to "🥚", "corn dog" to "🌭", "orange juice" to "🧃",
        "apple juice" to "🧃", "coconut milk" to "🥥", "almond milk" to "🥛", "soy milk" to "🥛",
        "black pepper" to "🧂", "brussels sprout" to "🥬", "green onion" to "🧅",
    )

    /** Words that name a whole aisle; a more specific word later in the name wins. */
    private val generic = setOf(
        "beverage", "drink", "sweet", "candy", "cereal", "snack", "nut", "seed", "sugar",
    )

    private val categories: List<Pair<String, String>> = listOf(
        "dairy" to "🥛", "egg" to "🥚", "spice" to "🧂", "baby" to "🍼", "fats" to "🫒", "poultry" to "🍗",
        "soup" to "🍲", "sausage" to "🌭", "breakfast cereal" to "🥣", "fruit" to "🍎", "pork" to "🥓",
        "vegetable" to "🥦", "nut" to "🌰", "beef" to "🥩", "beverage" to "🥤", "fish" to "🐟",
        "legume" to "🫘", "lamb" to "🍖", "baked" to "🍞", "sweet" to "🍬", "cereal grain" to "🌾",
        "fast food" to "🍔", "meal" to "🍱", "snack" to "🍿", "restaurant" to "🍽️",
    )

    private val wordSplit = Regex("[^a-z]+")

    private fun singular(w: String) = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
        w.length > 4 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes")) -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Emoji for a food from its name, falling back to its USDA category and where it came from. */
    fun food(name: String, category: String? = null, source: FoodSource? = null): String {
        val raw = name.lowercase().split(wordSplit).filter { it.isNotEmpty() }
        val words = raw.map { if (it in keywords) it else singular(it) }
        for (i in 0 until raw.size - 1) {
            pairs["${raw[i]} ${raw[i + 1]}"]?.let { return it }
            pairs["${singular(raw[i])} ${singular(raw[i + 1])}"]?.let { return it }
        }
        var fallback: String? = null
        for (word in words) {
            val hit = keywords[word] ?: continue
            if (word in generic || singular(word) in generic) {
                if (fallback == null) fallback = hit
            } else {
                return hit
            }
        }
        fallback?.let { return it }
        val cat = category?.lowercase().orEmpty()
        if (cat.isNotEmpty()) {
            categories.firstOrNull { cat.contains(it.first) }?.let { return it.second }
            for (word in cat.split(wordSplit)) keywords[singular(word)]?.let { return it }
        }
        return when (source) {
            FoodSource.RECIPE -> "🍲"
            FoodSource.CUSTOM -> "🏷️"
            else -> "🍽️"
        }
    }

    fun meal(meal: Int): String = when (meal) {
        0 -> "🍳"
        1 -> "🥗"
        2 -> "🍝"
        else -> "🍿"
    }

    fun group(g: NutrientGroup): String = when (g) {
        NutrientGroup.GENERAL -> "⚡"
        NutrientGroup.CARBS -> "🍞"
        NutrientGroup.LIPIDS -> "🥑"
        NutrientGroup.PROTEIN -> "💪"
        NutrientGroup.VITAMINS -> "🍊"
        NutrientGroup.MINERALS -> "💎"
    }

    /** A memorable emoji per nutrient, often a food that is rich in it. */
    fun nutrient(id: Int): String = when (id) {
        N.ENERGY -> "🔥"
        N.ALCOHOL -> "🍷"
        N.CAFFEINE -> "☕"
        N.WATER -> "💧"
        N.CARBS -> "🍞"
        N.FIBER -> "🫘"
        N.STARCH -> "🥔"
        N.SUGARS -> "🍬"
        N.ADDED_SUGARS -> "🧁"
        N.FAT -> "🥑"
        N.SAT_FAT -> "🧈"
        N.MUFA -> "🫒"
        N.PUFA, N.LA -> "🌻"
        N.ALA -> "🌱"
        N.EPA, N.DHA, N.DPA -> "🐟"
        N.TRANS_FAT -> "🍟"
        N.CHOLESTEROL -> "🥚"
        N.PROTEIN -> "💪"
        N.VIT_A, N.BETA_CAROTENE -> "🥕"
        N.LUTEIN -> "🥬"
        N.LYCOPENE -> "🍅"
        N.VIT_C -> "🍊"
        N.VIT_D -> "☀️"
        N.VIT_E -> "🌻"
        N.VIT_K -> "🥬"
        N.THIAMIN -> "🌾"
        N.RIBOFLAVIN -> "🥛"
        N.NIACIN -> "🍗"
        N.PANTOTHENIC -> "🍄"
        N.VIT_B6 -> "🍌"
        N.BIOTIN -> "🥚"
        N.FOLATE -> "🥬"
        N.VIT_B12 -> "🥩"
        N.CHOLINE -> "🥚"
        N.CALCIUM -> "🦴"
        N.COPPER -> "🪙"
        N.IODINE -> "🌊"
        N.IRON -> "🧲"
        N.MAGNESIUM -> "🎃"
        N.MANGANESE -> "🍍"
        N.PHOSPHORUS -> "✨"
        N.POTASSIUM -> "🍌"
        N.SELENIUM -> "🌰"
        N.SODIUM -> "🧂"
        N.ZINC -> "🦪"
        N.FLUORIDE -> "🦷"
        else -> "🧬" // amino acids
    }

    /** Everyday foods rich in a nutrient, for "boost" suggestions. Each pair is (emoji, search term). */
    fun boosters(id: Int): List<Pair<String, String>> = when (id) {
        N.FIBER -> listOf("🫘" to "black beans", "🍐" to "pear", "🥣" to "oats", "🫐" to "raspberries")
        N.VIT_D -> listOf("🐟" to "salmon", "🥚" to "egg", "🥛" to "milk", "🍄" to "mushrooms")
        N.VIT_C -> listOf("🍊" to "orange", "🫑" to "red bell pepper", "🥝" to "kiwi", "🍓" to "strawberries")
        N.VIT_B12 -> listOf("🐟" to "tuna", "🥩" to "beef", "🥛" to "milk", "🥚" to "egg")
        N.CALCIUM -> listOf("🥛" to "milk", "🧀" to "cheddar", "🥛" to "yogurt", "🐟" to "sardines")
        N.IRON -> listOf("🥩" to "beef", "🫘" to "lentils", "🥬" to "spinach", "🦪" to "clams")
        N.MAGNESIUM -> listOf("🎃" to "pumpkin seeds", "🌰" to "almonds", "🥬" to "spinach", "🫘" to "black beans")
        N.POTASSIUM -> listOf("🥔" to "potato", "🍌" to "banana", "🫘" to "white beans", "🥑" to "avocado")
        N.PROTEIN -> listOf("🍗" to "chicken breast", "🥚" to "egg", "🥛" to "greek yogurt", "🫘" to "lentils")
        N.FOLATE -> listOf("🥬" to "spinach", "🫘" to "lentils", "🥑" to "avocado", "🥦" to "broccoli")
        N.ZINC -> listOf("🦪" to "oysters", "🥩" to "beef", "🎃" to "pumpkin seeds", "🫘" to "chickpeas")
        else -> emptyList()
    }
}

/** An emoji on a soft rounded tile. */
@Composable
fun EmojiTile(emoji: String, background: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = fontSize, maxLines = 1)
    }
}
