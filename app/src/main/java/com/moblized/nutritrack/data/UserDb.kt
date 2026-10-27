package com.moblized.nutritrack.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diary", indices = [Index("date")])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date, yyyy-MM-dd */
    val date: String,
    val meal: Int,
    val loggedAt: Long,
    val source: String,
    val refId: Long,
    val name: String,
    val quantity: Double,
    val unitLabel: String,
    val unitGrams: Double,
    val grams: Double,
    /** Nutrient totals for this entry (not per 100 g). */
    val nutrients: String,
)

@Entity(tableName = "custom_foods", indices = [Index("barcode")])
data class CustomFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val servingLabel: String = "1 serving",
    val servingGrams: Double = 100.0,
    /** Nutrients per 100 g. */
    val per100: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servings: Double = 1.0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Slug of the Mealie recipe this was imported from, if any. */
    val mealieSlug: String? = null,
)

@Entity(tableName = "recipe_items", indices = [Index("recipeId")])
data class RecipeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val source: String,
    val refId: Long,
    val name: String,
    val quantity: Double,
    val unitLabel: String,
    val unitGrams: Double,
    val grams: Double,
    /** Snapshot of the ingredient's nutrients per 100 g. */
    val per100: String,
)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary WHERE date = :date ORDER BY meal, loggedAt")
    fun observeDay(date: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary WHERE date = :date ORDER BY meal, loggedAt")
    suspend fun day(date: String): List<DiaryEntry>

    @Query("SELECT * FROM diary WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun range(from: String, to: String): List<DiaryEntry>

    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun get(id: Long): DiaryEntry?

    @Query("SELECT * FROM diary WHERE id IN (SELECT MAX(id) FROM diary GROUP BY source, refId) ORDER BY loggedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DiaryEntry>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomFood>>

    @Query("SELECT * FROM custom_foods WHERE id = :id")
    suspend fun get(id: Long): CustomFood?

    @Query("SELECT * FROM custom_foods WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): CustomFood?

    @Query("SELECT * FROM custom_foods WHERE name LIKE :pattern OR brand LIKE :pattern ORDER BY name COLLATE NOCASE LIMIT 30")
    suspend fun search(pattern: String): List<CustomFood>

    @Insert
    suspend fun insert(food: CustomFood): Long

    @Update
    suspend fun update(food: CustomFood)

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE mealieSlug = :slug LIMIT 1")
    suspend fun byMealieSlug(slug: String): Recipe?

    @Query("SELECT * FROM recipes WHERE mealieSlug IS NOT NULL")
    fun observeMealieLinked(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: Long): Recipe?

    @Query("SELECT * FROM recipes WHERE name LIKE :pattern ORDER BY name COLLATE NOCASE LIMIT 30")
    suspend fun search(pattern: String): List<Recipe>

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId ORDER BY id")
    suspend fun items(recipeId: Long): List<RecipeItem>

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Insert
    suspend fun insertItems(items: List<RecipeItem>)

    @Query("DELETE FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun deleteItems(recipeId: Long)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [DiaryEntry::class, CustomFood::class, Recipe::class, RecipeItem::class],
    version = 2,
    exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun diary(): DiaryDao
    abstract fun customFoods(): CustomFoodDao
    abstract fun recipes(): RecipeDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN mealieSlug TEXT")
    }
}
