package com.moblized.nutritrack.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.Room
import androidx.room.withTransaction
import com.moblized.nutritrack.alerts.LowNutrientAlerts
import com.moblized.nutritrack.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** App-wide dependencies and the operations the screens use. */
class AppGraph(private val context: Context) {
    val db: UserDatabase = Room.databaseBuilder(context, UserDatabase::class.java, "nutritrack.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val usda = UsdaDatabase(context)
    val settings = SettingsRepository(context)
    val health = HealthSync(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---------- foods ----------

    suspend fun loadFood(source: FoodSource, id: Long): Food? = withContext(Dispatchers.IO) {
        when (source) {
            FoodSource.USDA -> runCatching { usda.food(id) }.getOrNull()
            FoodSource.CUSTOM -> db.customFoods().get(id)?.toFood()
            FoodSource.RECIPE -> db.recipes().get(id)?.let { recipeFood(it) }
        }
    }

    suspend fun search(query: String): List<FoodSummary> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val pattern = "%" + q.replace(" ", "%") + "%"
        val recipes = db.recipes().search(pattern).map { r ->
            val food = recipeFood(r)
            FoodSummary(FoodSource.RECIPE, r.id, r.name, "Recipe", food.per100g[Nutrients.ENERGY])
        }
        val custom = db.customFoods().search(pattern).map { it.toSummary() }
        val usdaResults = runCatching { usda.search(q) }.getOrDefault(emptyList())
        recipes + custom + usdaResults
    }

    suspend fun recentFoods(limit: Int = 25): List<FoodSummary> = withContext(Dispatchers.IO) {
        db.diary().recent(limit).map { e ->
            val kcal = if (e.grams > 0) NutrientCodec.decode(e.nutrients)[Nutrients.ENERGY]?.let { it / e.grams * 100 } else null
            FoodSummary(FoodSource.of(e.source), e.refId, e.name, "Recently logged", kcal)
        }
    }

    fun CustomFood.toFood(): Food = Food(
        source = FoodSource.CUSTOM,
        id = id,
        name = name,
        detail = listOfNotNull(brand.ifBlank { null }, if (barcode != null) "Scanned" else "My food").joinToString(" · "),
        per100g = NutrientCodec.decode(per100),
        portions = listOf(Portion(servingLabel, servingGrams)),
    )

    private fun CustomFood.toSummary() = FoodSummary(
        FoodSource.CUSTOM, id, name,
        listOfNotNull(brand.ifBlank { null }, if (barcode != null) "Scanned" else "My food").joinToString(" · "),
        NutrientCodec.decode(per100)[Nutrients.ENERGY],
    )

    suspend fun recipeFood(r: Recipe): Food {
        val items = db.recipes().items(r.id)
        val total = items.sumOf { it.grams }
        val sum = sumNutrients(items.map { NutrientCodec.decode(it.per100).scaled(it.grams / 100.0) })
        val per100 = if (total > 0) sum.scaled(100.0 / total) else emptyMap()
        val servings = r.servings.coerceAtLeast(1.0)
        val portions = if (total > 0) {
            listOf(Portion("serving", total / servings), Portion("whole recipe", total))
        } else emptyList()
        return Food(FoodSource.RECIPE, r.id, r.name, "Recipe · ${fmtServings(servings)} servings", per100, portions)
    }

    private fun fmtServings(s: Double) = if (s % 1.0 == 0.0) s.toInt().toString() else s.toString()

    // ---------- diary ----------

    suspend fun saveEntry(
        existingId: Long,
        date: LocalDate,
        meal: Int,
        food: Food,
        quantity: Double,
        unit: Portion,
    ): Long {
        val grams = quantity * unit.grams
        val entry = DiaryEntry(
            id = existingId,
            date = date.toString(),
            meal = meal,
            loggedAt = System.currentTimeMillis(),
            source = food.source.key,
            refId = food.id,
            name = food.name,
            quantity = quantity,
            unitLabel = unit.label,
            unitGrams = unit.grams,
            grams = grams,
            nutrients = NutrientCodec.encode(food.per100g.scaled(grams / 100.0)),
        )
        val id = if (existingId > 0) {
            db.diary().update(entry); existingId
        } else {
            db.diary().insert(entry)
        }
        afterDiaryChange(syncEntry = entry.copy(id = id))
        return id
    }

    suspend fun deleteEntry(id: Long) {
        db.diary().delete(id)
        afterDiaryChange(deletedId = id)
    }

    private fun afterDiaryChange(syncEntry: DiaryEntry? = null, deletedId: Long? = null) {
        scope.launch {
            runCatching { TodayWidget().updateAll(context) }
            if (settings.current().healthConnect) {
                syncEntry?.let { health.upsert(listOf(it)) }
                deletedId?.let { health.delete(it) }
            }
        }
    }

    /** Pushes the last [days] days of the diary to Health Connect. */
    suspend fun syncRecent(days: Long = 30): Int {
        val to = LocalDate.now()
        val entries = db.diary().range(to.minusDays(days).toString(), to.toString())
        return health.upsert(entries)
    }

    // ---------- what to eat ----------

    @Volatile
    private var pool: List<SuggestCandidate>? = null

    /** Everyday foods to suggest, loaded once. */
    suspend fun suggestionPool(): List<SuggestCandidate> = withContext(Dispatchers.IO) {
        pool ?: usda.suggestionPool().also { pool = it }
    }

    /**
     * The person's own foods: what they've logged in the last 30 days (in the amount they usually have),
     * plus their recipes and saved foods.
     */
    suspend fun personalCandidates(today: LocalDate): List<SuggestCandidate> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, SuggestCandidate>()
        val recent = db.diary().range(today.minusDays(30).toString(), today.toString()).sortedByDescending { it.loggedAt }
        for (e in recent) {
            val key = "${e.source}:${e.refId}"
            if (key in out || e.grams <= 0) continue
            out[key] = SuggestCandidate(
                source = FoodSource.of(e.source), id = e.refId, name = e.name, category = null,
                group = FoodGroup.MINE, quantity = e.quantity, unitLabel = e.unitLabel, unitGrams = e.unitGrams,
                nutrients = NutrientCodec.decode(e.nutrients),
            )
        }
        for (r in db.recipes().all()) {
            val key = "${FoodSource.RECIPE.key}:${r.id}"
            if (key in out) continue
            val f = recipeFood(r)
            val serving = f.portions.firstOrNull() ?: continue
            out[key] = SuggestCandidate(
                FoodSource.RECIPE, r.id, r.name, null, FoodGroup.MINE, 1.0, serving.label, serving.grams,
                f.per100g.scaled(serving.grams / 100.0),
            )
        }
        for (cf in db.customFoods().all()) {
            val key = "${FoodSource.CUSTOM.key}:${cf.id}"
            if (key in out) continue
            out[key] = SuggestCandidate(
                FoodSource.CUSTOM, cf.id, cf.name, null, FoodGroup.MINE, 1.0, cf.servingLabel, cf.servingGrams,
                NutrientCodec.decode(cf.per100).scaled(cf.servingGrams / 100.0),
            )
        }
        out.values.toList()
    }

    // ---------- smart scale (via Health Connect) ----------

    /** Copies the newest scale weigh-in from Health Connect into the profile. Returns it when applied. */
    suspend fun syncWeight(): HealthSync.WeighIn? {
        val s = settings.current()
        if (!s.weightSync) return null
        val w = health.latestWeighIn() ?: return null
        val t = w.time.toEpochMilli()
        if (t <= s.lastWeighInTime) return null
        settings.applyWeighIn(w.kg, t, appLabel(w.sourcePackage))
        refreshWidget()
        return w
    }

    fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ---------- Mealie ----------

    suspend fun mealie(): MealieClient? {
        val s = settings.current()
        return if (s.mealieConfigured) MealieClient(s.mealieUrl, s.mealieToken) else null
    }

    /** Makes sure the evening low-nutrient check is scheduled (or cancelled) to match the settings. */
    fun syncAlerts(replace: Boolean) {
        scope.launch {
            val s = settings.current()
            if (s.lowNotify) {
                LowNutrientAlerts.schedule(context, s.lowNotifyMinutes, replace)
            } else {
                LowNutrientAlerts.cancel(context)
            }
        }
    }

    fun refreshWidget() {
        scope.launch { runCatching { TodayWidget().updateAll(context) } }
    }

    // ---------- custom foods & recipes ----------

    suspend fun saveCustomFood(food: CustomFood): Long =
        if (food.id > 0) {
            db.customFoods().update(food); food.id
        } else db.customFoods().insert(food)

    suspend fun saveRecipe(recipe: Recipe, items: List<RecipeItem>): Long = db.withTransaction {
        val id = if (recipe.id > 0) {
            db.recipes().update(recipe); recipe.id
        } else db.recipes().insert(recipe)
        db.recipes().deleteItems(id)
        db.recipes().insertItems(items.map { it.copy(id = 0, recipeId = id) })
        id
    }

    suspend fun deleteRecipe(id: Long) = db.withTransaction {
        db.recipes().deleteItems(id)
        db.recipes().delete(id)
    }
}

object Graph {
    lateinit var app: AppGraph
}
