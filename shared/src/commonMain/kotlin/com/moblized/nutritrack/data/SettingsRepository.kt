package com.moblized.nutritrack.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** Keep the profile weight in sync with the latest weigh-in in Health Connect (smart scale). */
    val weightSync: Boolean = false,
    val lastWeighInTime: Long = 0L,
    val lastWeighInSource: String = "",
    val mealieUrl: String = "",
    val mealieToken: String = "",
    /** Show "running low" warnings in the app. */
    val lowWarnings: Boolean = true,
    /** Send an evening notification when something is low. */
    val lowNotify: Boolean = false,
    /** Time of the evening check, in minutes after midnight. */
    val lowNotifyMinutes: Int = 19 * 60 + 30,
    /** All 68 nutrients (vitamins, minerals, amino acids, fats); off shows just the label essentials. */
    val detailedNutrition: Boolean = true,
) {
    val mealieConfigured: Boolean get() = mealieUrl.isNotBlank() && mealieToken.isNotBlank()
}

/** File name of the settings store; on Android it lives where the app always kept it (files/datastore/). */
const val SETTINGS_FILE = "settings.preferences_pb"

class SettingsRepository(private val store: DataStore<Preferences>) {
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
        val weightSync = booleanPreferencesKey("weight_sync")
        val weighInTime = longPreferencesKey("weigh_in_time")
        val weighInSource = stringPreferencesKey("weigh_in_source")
        val mealieUrl = stringPreferencesKey("mealie_url")
        val mealieToken = stringPreferencesKey("mealie_token")
        val lowWarnings = booleanPreferencesKey("low_warnings")
        val lowNotify = booleanPreferencesKey("low_notify")
        val lowNotifyMinutes = intPreferencesKey("low_notify_minutes")
        val detailed = booleanPreferencesKey("detailed_nutrition")
        val updateChecked = longPreferencesKey("update_checked")
        val updateSnoozedCode = intPreferencesKey("update_snoozed_code")
        val updateSnoozedUntil = longPreferencesKey("update_snoozed_until")
    }

    val flow: Flow<Settings> = store.data.map { it.toSettings() }

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
            weightSync = this[K.weightSync] ?: false,
            lastWeighInTime = this[K.weighInTime] ?: 0L,
            lastWeighInSource = this[K.weighInSource] ?: "",
            mealieUrl = this[K.mealieUrl] ?: "",
            mealieToken = this[K.mealieToken] ?: "",
            lowWarnings = this[K.lowWarnings] ?: d.lowWarnings,
            lowNotify = this[K.lowNotify] ?: d.lowNotify,
            lowNotifyMinutes = this[K.lowNotifyMinutes] ?: d.lowNotifyMinutes,
            detailedNutrition = this[K.detailed] ?: d.detailedNutrition,
        )
    }

    suspend fun updateProfile(p: Profile) {
        store.edit {
            it[K.sex] = p.sex.name
            it[K.age] = p.age
            it[K.height] = p.heightCm
            it[K.weight] = p.weightKg
            it[K.activity] = p.activity.name
            it[K.goal] = p.goal.name
            it[K.profileSet] = true
        }
    }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[K.theme] = mode.name }
    suspend fun setPureBlack(on: Boolean) = store.edit { it[K.pureBlack] = on }
    suspend fun setUnits(u: Units) = store.edit { it[K.units] = u.name }
    suspend fun setHealthConnect(on: Boolean) = store.edit { it[K.health] = on }
    suspend fun setWeightSync(on: Boolean) = store.edit { it[K.weightSync] = on }

    /** Applies a weigh-in from the scale to the profile and remembers it so it is only applied once. */
    suspend fun applyWeighIn(kg: Double, time: Long, source: String) = store.edit {
        it[K.weight] = kg
        it[K.weighInTime] = time
        it[K.weighInSource] = source
    }

    suspend fun setLowWarnings(on: Boolean) = store.edit { it[K.lowWarnings] = on }
    suspend fun setDetailedNutrition(on: Boolean) = store.edit { it[K.detailed] = on }
    suspend fun setLowNotify(on: Boolean) = store.edit { it[K.lowNotify] = on }
    suspend fun setLowNotifyMinutes(minutes: Int) = store.edit { it[K.lowNotifyMinutes] = minutes }

    suspend fun setMealie(url: String, token: String) = store.edit {
        it[K.mealieUrl] = url.trim().trimEnd('/')
        it[K.mealieToken] = token.trim()
    }

    suspend fun setCalorieOverride(kcal: Int?) = store.edit {
        it.remove(K.kcal)
        if (kcal != null && kcal > 0) it[K.kcal] = kcal
    }

    suspend fun setMacros(protein: Int, carbs: Int, fat: Int) = store.edit {
        it[K.protein] = protein
        it[K.carbs] = carbs
        it[K.fat] = fat
    }

    suspend fun setOverride(id: Int, value: Double?) = store.edit {
        val m = NutrientCodec.decode(it[K.overrides]).toMutableMap()
        m.remove(id)
        if (value != null) m[id] = value
        it[K.overrides] = NutrientCodec.encode(m)
    }

    suspend fun clearOverrides() = store.edit { it.remove(K.overrides) }

    // ---------- update checks (not part of Settings: nothing on screen depends on them) ----------

    data class UpdateState(val checkedAt: Long, val snoozedCode: Int, val snoozedUntil: Long)

    suspend fun updateState(): UpdateState {
        val p = store.data.first()
        return UpdateState(p[K.updateChecked] ?: 0L, p[K.updateSnoozedCode] ?: 0, p[K.updateSnoozedUntil] ?: 0L)
    }

    suspend fun setUpdateChecked(at: Long) = store.edit { it[K.updateChecked] = at }

    suspend fun snoozeUpdate(code: Int, until: Long) = store.edit {
        it[K.updateSnoozedCode] = code
        it[K.updateSnoozedUntil] = until
    }
}
