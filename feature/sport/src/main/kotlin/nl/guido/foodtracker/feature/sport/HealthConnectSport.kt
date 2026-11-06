package nl.guido.foodtracker.feature.sport

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.data.repo.SportRepository
import nl.guido.foodtracker.core.model.Id
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Whether this phone can read sport from other apps. */
internal enum class LinkState {
    /** Health Connect doesn't work on this phone. */
    UNAVAILABLE,

    /** Health Connect needs installing or updating first (Android 10 to 13). */
    NEEDS_INSTALL,

    /** Works, but the person hasn't allowed reading yet. */
    NOT_LINKED,
    LINKED,
}

/**
 * Reads exercise sessions and their calories from Health Connect (round 11 P3), where apps like
 * Garmin Connect put them. Read-only: this app never writes to Health Connect.
 */
@Singleton
internal class HealthConnectSport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sports: SportRepository,
) {
    /** Exercise sessions and calories, read-only. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    val permissionContract = PermissionController.createRequestPermissionResultContract()

    private fun client(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    suspend fun state(): LinkState = withContext(Dispatchers.IO) {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }
                    .getOrDefault(emptySet())
                // Sessions plus at least one kind of calories is enough to import.
                if (permissions.first() in granted && granted.any { it in permissions.drop(1) }) LinkState.LINKED else LinkState.NOT_LINKED
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> LinkState.NEEDS_INSTALL
            else -> LinkState.UNAVAILABLE
        }
    }

    /** Opens the Play Store page of Health Connect, to install or update it. */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Imports new sessions of the last [SportImport.DAYS_BACK] days; returns how many lines were added. */
    suspend fun import(userId: Id, now: Instant = Instant.now()): Int = withContext(Dispatchers.IO) {
        val client = client() ?: return@withContext 0
        val range = TimeRangeFilter.between(now.minus(SportImport.DAYS_BACK, ChronoUnit.DAYS), now)
        val sessions = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = range)).records
        val known = sports.importedIds(userId)
        var added = 0
        for (s in sessions) {
            if (s.metadata.id in known) continue
            val totals = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime),
                    ),
                )
            }.getOrNull()
            val session = ImportedSession(
                recordId = s.metadata.id,
                start = s.startTime,
                end = s.endTime,
                startOffset = s.startZoneOffset,
                title = s.title,
                typeName = context.getString(exerciseName(s.exerciseType)),
                activeKcal = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
                totalKcal = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            )
            val entry = SportImport.toEntry(session, userId, known) ?: continue
            sports.save(entry)
            added++
        }
        added
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}

/** A readable name for the common exercise types; everything else is just "Sport". */
internal fun exerciseName(type: Int): Int = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> R.string.sport_type_running
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> R.string.sport_type_cycling
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> R.string.sport_type_walking
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> R.string.sport_type_swimming
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> R.string.sport_type_strength
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> R.string.sport_type_yoga
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> R.string.sport_type_hiit
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE, ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> R.string.sport_type_rowing
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> R.string.sport_type_cross_trainer
    else -> R.string.sport_type_other
}
