package com.moblized.nutritrack.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

enum class Activity(val label: String, val factor: Double) {
    SEDENTARY("Sedentary (desk job, little exercise)", 1.2),
    LIGHT("Lightly active (1–3 workouts/week)", 1.375),
    MODERATE("Moderately active (3–5 workouts/week)", 1.55),
    VERY("Very active (6–7 workouts/week)", 1.725),
    EXTRA("Extremely active (physical job + training)", 1.9),
}

enum class Goal(val label: String, val kcalDelta: Int) {
    LOSE_FAST("Lose 1 lb per week", -500),
    LOSE("Lose 0.5 lb per week", -250),
    MAINTAIN("Maintain weight", 0),
    GAIN("Gain 0.5 lb per week", 250),
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class Units(val label: String) { US("US (lb, ft)"), METRIC("Metric (kg, cm)") }

data class Profile(
    val sex: Sex = Sex.MALE,
    val age: Int = 35,
    val heightCm: Double = 178.0,
    val weightKg: Double = 82.0,
    val activity: Activity = Activity.LIGHT,
    val goal: Goal = Goal.MAINTAIN,
)

data class Settings(
    val profile: Profile = Profile(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val units: Units = Units.US,
    val calorieOverride: Int? = null,
    val proteinPct: Int = 30,
    val carbPct: Int = 40,
    val fatPct: Int = 30,
    val overrides: Map<Int, Double> = emptyMap(),
    val healthConnect: Boolean = false,
    val profileSet: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val sex = stringPreferencesKey("sex")
        val age = intPreferencesKey("age")
        val height = doublePreferencesKey("height_cm")
        val weight = doublePreferencesKey("weight_kg")
        val activity = stringPreferencesKey("activity")
        val goal = stringPreferencesKey("goal")
        val theme = stringPreferencesKey("theme")
        val pureBlack = booleanPreferencesKey("pure_black")
        val units = stringPreferencesKey("units")
        val kcal = intPreferencesKey("kcal_override")
        val protein = intPreferencesKey("protein_pct")
        val carbs = intPreferencesKey("carb_pct")
        val fat = intPreferencesKey("fat_pct")
        val overrides = stringPreferencesKey("target_overrides")
        val health = booleanPreferencesKey("health_connect")
        val profileSet = booleanPreferencesKey("profile_set")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = flow.first()

    private inline fun <reified T : Enum<T>> parse(v: String?, default: T): T =
        v?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            profile = Profile(
                sex = parse(this[K.sex], d.profile.sex),
                age = this[K.age] ?: d.profile.age,
                heightCm = this[K.height] ?: d.profile.heightCm,
                weightKg = this[K.weight] ?: d.profile.weightKg,
                activity = parse(this[K.activity], d.profile.activity),
                goal = parse(this[K.goal], d.profile.goal),
            ),
            themeMode = parse(this[K.theme], d.themeMode),
            pureBlack = this[K.pureBlack] ?: d.pureBlack,
            units = parse(this[K.units], d.units),
            calorieOverride = this[K.kcal]?.takeIf { it > 0 },
            proteinPct = this[K.protein] ?: d.proteinPct,
            carbPct = this[K.carbs] ?: d.carbPct,
            fatPct = this[K.fat] ?: d.fatPct,
            overrides = NutrientCodec.decode(this[K.overrides]),
            healthConnect = this[K.health] ?: d.healthConnect,
            profileSet = this[K.profileSet] ?: false,
        )
    }

    suspend fun updateProfile(p: Profile) {
        context.dataStore.edit {
            it[K.sex] = p.sex.name
            it[K.age] = p.age
            it[K.height] = p.heightCm
            it[K.weight] = p.weightKg
            it[K.activity] = p.activity.name
            it[K.goal] = p.goal.name
            it[K.profileSet] = true
        }
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setPureBlack(on: Boolean) = context.dataStore.edit { it[K.pureBlack] = on }
    suspend fun setUnits(u: Units) = context.dataStore.edit { it[K.units] = u.name }
    suspend fun setHealthConnect(on: Boolean) = context.dataStore.edit { it[K.health] = on }

    suspend fun setCalorieOverride(kcal: Int?) = context.dataStore.edit {
        it.remove(K.kcal)
        if (kcal != null && kcal > 0) it[K.kcal] = kcal
    }

    suspend fun setMacros(protein: Int, carbs: Int, fat: Int) = context.dataStore.edit {
        it[K.protein] = protein
        it[K.carbs] = carbs
        it[K.fat] = fat
    }

    suspend fun setOverride(id: Int, value: Double?) = context.dataStore.edit {
        val m = NutrientCodec.decode(it[K.overrides]).toMutableMap()
        m.remove(id)
        if (value != null) m[id] = value
        it[K.overrides] = NutrientCodec.encode(m)
    }

    suspend fun clearOverrides() = context.dataStore.edit { it.remove(K.overrides) }
}
