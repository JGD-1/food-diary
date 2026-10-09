package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.EnergyEstimator
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.TargetBreakdown
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The daily target: Mifflin-St Jeor × activity factor, minus (or plus) the kcal for the chosen pace,
 * plus a gradual correction learned from weekly weigh-ins. A number the user typed wins.
 */
internal class AdaptiveEnergyEstimator(private val today: Today) : EnergyEstimator {

    override fun dailyTarget(profile: UserProfile, weighIns: List<WeighIn>, intake: List<DayTotal>): TargetBreakdown =
        details(profile, weighIns, intake).breakdown

    /** Everything the "how is my target calculated" screen shows. */
    fun details(profile: UserProfile, weighIns: List<WeighIn>, intake: List<DayTotal>): EnergyDetails {
        val date = today.date()
        val latest = weighIns.maxByOrNull { it.date }
        val weight = latest?.kg ?: profile.startWeightKg
        val age = EnergyMath.age(profile.birthYear, date)
        val resting = EnergyMath.restingKcal(weight, profile.heightCm, age, profile.sex).roundToInt()
        val maintenance = (resting * profile.activity.factor).roundToInt()
        val direction = EnergyMath.direction(weight, profile.targetWeightKg)
        val paceKcal = EnergyMath.kcalPerDayForPace(profile.weeklyPaceKg).roundToInt()
        val deficit = when (direction) {
            Direction.LOSE -> paceKcal
            Direction.GAIN -> -paceKcal
            Direction.MAINTAIN -> 0
        }
        val steps = EnergyMath.learnAdjustment(profile, weighIns, intake, date)
        val adjustment = steps.lastOrNull()?.adjustmentKcal ?: 0
        val calculated = maintenance - deficit + adjustment
        val target = calculated.coerceAtLeast(EnergyMath.MIN_TARGET_KCAL)
        val manual = profile.manualTargetKcal
        return EnergyDetails(
            breakdown = TargetBreakdown(
                bmrKcal = resting,
                activityFactor = profile.activity.factor,
                maintenanceKcal = maintenance,
                deficitKcal = deficit,
                adjustmentKcal = adjustment,
                targetKcal = manual ?: target,
                isManual = manual != null,
            ),
            calculatedTargetKcal = target,
            keptAtMinimum = target != calculated,
            age = age,
            sex = profile.sex,
            weightKg = weight,
            weightDate = latest?.date,
            direction = direction,
            paceKg = profile.weeklyPaceKg,
            weeklyWeighIns = EnergyMath.weeklyWeighIns(weighIns).size,
            adjustmentSteps = steps,
        )
    }
}

/** Today's date, swappable in tests. */
internal fun interface Today {
    fun date(): LocalDate
}

internal data class EnergyDetails(
    val breakdown: TargetBreakdown,
    /** The formula's result, also when the user set their own number. */
    val calculatedTargetKcal: Int,
    /** True when the formula came out under [EnergyMath.MIN_TARGET_KCAL] and was raised to it. */
    val keptAtMinimum: Boolean,
    val age: Int,
    val sex: Sex,
    val weightKg: Double,
    /** Date of the weigh-in used, or null when the starting weight was used. */
    val weightDate: LocalDate?,
    val direction: Direction,
    val paceKg: Double,
    val weeklyWeighIns: Int,
    val adjustmentSteps: List<AdjustmentStep>,
) {
    /** Weekly weigh-ins still needed before learning starts (0 once it has started). */
    val weighInsUntilLearning: Int get() = (EnergyMath.MIN_WEEKLY_WEIGH_INS - weeklyWeighIns).coerceAtLeast(0)
}
