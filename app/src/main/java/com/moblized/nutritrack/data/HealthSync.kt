package com.moblized.nutritrack.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Writes diary entries to Health Connect (which Samsung Health can read) and reads scale weigh-ins. */
class HealthSync(private val context: Context) : HealthService {

    val permissions: Set<String> = setOf(HealthPermission.getWritePermission(NutritionRecord::class))

    fun available(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    override suspend fun isAvailable(): Boolean = available()

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermission(): Boolean = available() && runCatching {
        client().permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    override suspend fun upsert(entries: List<DiaryEntry>): Int {
        if (entries.isEmpty() || !hasPermission()) return 0
        return runCatching {
            entries.chunked(500).forEach { batch -> client().insertRecords(batch.map(::toRecord)) }
            entries.size
        }.getOrDefault(0)
    }

    override suspend fun delete(entryId: Long) {
        if (!hasPermission()) return
        runCatching {
            client().deleteRecords(
                NutritionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(clientId(entryId)),
            )
        }
    }

    private fun clientId(id: Long) = "nutritrack-entry-$id"

    // ---------- weight from a smart scale ----------

    val weightPermissions: Set<String> = setOf(HealthPermission.getReadPermission(WeightRecord::class))

    suspend fun hasWeightPermission(): Boolean = available() && runCatching {
        client().permissionController.getGrantedPermissions().containsAll(weightPermissions)
    }.getOrDefault(false)

    /** The most recent weight any app (e.g. a smart-scale app) saved to Health Connect in the last 30 days. */
    override suspend fun latestWeighIn(): WeighIn? {
        if (!hasWeightPermission()) return null
        return runCatching {
            val response = client().readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(30))),
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            )
            response.records.firstOrNull()?.let {
                WeighIn(it.weight.inKilograms, it.time.toEpochMilli(), it.metadata.dataOrigin.packageName)
            }
        }.getOrNull()
    }

    private fun toRecord(e: DiaryEntry): NutritionRecord {
        val n = NutrientCodec.decode(e.nutrients)
        val zone = ZoneId.systemDefault()
        val time = when (e.meal) {
            0 -> LocalTime.of(8, 0)
            1 -> LocalTime.of(12, 30)
            2 -> LocalTime.of(18, 30)
            else -> LocalTime.of(15, 30)
        }.plusSeconds((e.id % 600))
        val start = LocalDate.parse(e.date).atTime(time).atZone(zone).toInstant()
        val end = start.plusSeconds(60)
        val offset = zone.rules.getOffset(start)

        fun g(id: Int) = n[id]?.let { Mass.grams(it) }
        fun mg(id: Int) = n[id]?.let { Mass.milligrams(it) }
        fun ug(id: Int) = n[id]?.let { Mass.micrograms(it) }

        return NutritionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            name = e.name.take(100),
            mealType = when (e.meal) {
                0 -> MealType.MEAL_TYPE_BREAKFAST
                1 -> MealType.MEAL_TYPE_LUNCH
                2 -> MealType.MEAL_TYPE_DINNER
                else -> MealType.MEAL_TYPE_SNACK
            },
            energy = n[Nutrients.ENERGY]?.let { Energy.kilocalories(it) },
            protein = g(Nutrients.PROTEIN),
            totalCarbohydrate = g(Nutrients.CARBS),
            totalFat = g(Nutrients.FAT),
            saturatedFat = g(Nutrients.SAT_FAT),
            monounsaturatedFat = g(Nutrients.MUFA),
            polyunsaturatedFat = g(Nutrients.PUFA),
            transFat = g(Nutrients.TRANS_FAT),
            cholesterol = mg(Nutrients.CHOLESTEROL),
            dietaryFiber = g(Nutrients.FIBER),
            sugar = g(Nutrients.SUGARS),
            caffeine = mg(Nutrients.CAFFEINE),
            sodium = mg(Nutrients.SODIUM),
            potassium = mg(Nutrients.POTASSIUM),
            calcium = mg(Nutrients.CALCIUM),
            iron = mg(Nutrients.IRON),
            magnesium = mg(Nutrients.MAGNESIUM),
            zinc = mg(Nutrients.ZINC),
            phosphorus = mg(Nutrients.PHOSPHORUS),
            copper = mg(Nutrients.COPPER),
            manganese = mg(Nutrients.MANGANESE),
            selenium = ug(Nutrients.SELENIUM),
            iodine = ug(Nutrients.IODINE),
            vitaminA = ug(Nutrients.VIT_A),
            vitaminC = mg(Nutrients.VIT_C),
            vitaminD = ug(Nutrients.VIT_D),
            vitaminE = mg(Nutrients.VIT_E),
            vitaminK = ug(Nutrients.VIT_K),
            thiamin = mg(Nutrients.THIAMIN),
            riboflavin = mg(Nutrients.RIBOFLAVIN),
            niacin = mg(Nutrients.NIACIN),
            pantothenicAcid = mg(Nutrients.PANTOTHENIC),
            vitaminB6 = mg(Nutrients.VIT_B6),
            biotin = ug(Nutrients.BIOTIN),
            folate = ug(Nutrients.FOLATE),
            vitaminB12 = ug(Nutrients.VIT_B12),
            metadata = Metadata(
                clientRecordId = clientId(e.id),
                clientRecordVersion = System.currentTimeMillis(),
            ),
        )
    }
}
