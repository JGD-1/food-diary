package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

/** The numbers behind the daily target. Plain Kotlin, no Android, so it is easy to test. */
internal object EnergyMath {
    /** Roughly how many kcal one kilo of body weight stands for. */
    const val KCAL_PER_KG = 7700.0

    /** The learned correction moves at most this much per weekly weigh-in (plan.md: ±100 kcal a week). */
    const val MAX_STEP_KCAL = 100

    /** The learned correction never goes beyond this, so a few weeks of patchy logging can't run away. */
    const val MAX_ADJUSTMENT_KCAL = 500

    /** Learning starts at the third weekly weigh-in (plan.md). */
    const val MIN_WEEKLY_WEIGH_INS = 3

    /** Gaps smaller than this are noise and leave the correction as it is. */
    const val DEAD_ZONE_KCAL = 50

    /** Days with less than this logged are treated as "not fully logged" and skipped. */
    const val MIN_LOGGED_DAY_KCAL = 500.0

    /** At least this share of the days between weigh-ins must be logged before we learn from them. */
    const val MIN_LOGGED_SHARE = 0.5

    /** The calculated target never goes below this. */
    const val MIN_TARGET_KCAL = 1200

    /** Within this many kg of the target weight we aim for maintenance. */
    const val AT_TARGET_MARGIN_KG = 0.5

    fun age(birthYear: Int, today: LocalDate): Int = today.year - birthYear

    /** Mifflin-St Jeor: energy used at complete rest, in kcal a day. */
    fun restingKcal(weightKg: Double, heightCm: Int, age: Int, sex: Sex): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Sex.MALE) 5 else -161

    /** kcal a day for a pace in kg a week. */
    fun kcalPerDayForPace(paceKg: Double): Double = paceKg * KCAL_PER_KG / 7

    /** Which way the person is heading. */
    fun direction(currentKg: Double, targetKg: Double): Direction = when {
        currentKg - targetKg > AT_TARGET_MARGIN_KG -> Direction.LOSE
        targetKg - currentKg > AT_TARGET_MARGIN_KG -> Direction.GAIN
        else -> Direction.MAINTAIN
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** One weigh-in per week (Monday to Sunday): the latest one of that week, oldest first. */
    fun weeklyWeighIns(weighIns: List<WeighIn>): List<WeighIn> =
        weighIns.groupBy { weekStart(it.date) }
            .map { (_, week) -> week.maxBy { it.date } }
            .sortedBy { it.date }

    /** Straight-line trend through the points, in kg per day. Smooths out single high or low weigh-ins. */
    fun slopeKgPerDay(points: List<WeighIn>): Double {
        if (points.size < 2) return 0.0
        val origin = points.first().date
        val xs = points.map { ChronoUnit.DAYS.between(origin, it.date).toDouble() }
        val ys = points.map { it.kg }
        val meanX = xs.average()
        val meanY = ys.average()
        val denominator = xs.sumOf { (it - meanX) * (it - meanX) }
        if (denominator == 0.0) return 0.0
        return xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / denominator
    }

    /**
     * Replays the weekly weigh-ins from the start and returns the learned correction after each one
     * (positive = the body uses more than the formula says, so eat more).
     *
     * From the third weekly weigh-in on, it compares what was eaten with how the weight trend moved:
     * maintenance = average eaten − trend × 7,700 kcal/kg. The correction moves half-way towards that,
     * at most [MAX_STEP_KCAL] per week. Weeks with too little logging leave it unchanged, so a
     * missed week never punishes the next one.
     */
    fun learnAdjustment(profile: UserProfile, weighIns: List<WeighIn>, intake: List<DayTotal>, today: LocalDate): List<AdjustmentStep> {
        val weekly = weeklyWeighIns(weighIns)
        val age = age(profile.birthYear, today)
        val steps = mutableListOf<AdjustmentStep>()
        var adjustment = 0
        for (i in MIN_WEEKLY_WEIGH_INS - 1 until weekly.size) {
            val window = weekly.subList(maxOf(0, i - 3), i + 1)
            val from = window.first().date
            val to = window.last().date
            val days = ChronoUnit.DAYS.between(from, to)
            val logged = intake.filter { it.date >= from && it.date < to && it.kcal >= MIN_LOGGED_DAY_KCAL }
            if (days > 0 && logged.size >= days * MIN_LOGGED_SHARE) {
                val observed = logged.map { it.kcal }.average() - slopeKgPerDay(window) * KCAL_PER_KG
                val formula = restingKcal(window.last().kg, profile.heightCm, age, profile.sex) * profile.activity.factor
                val gap = observed - formula - adjustment
                if (abs(gap) >= DEAD_ZONE_KCAL) {
                    val step = (gap / 2).roundToInt().coerceIn(-MAX_STEP_KCAL, MAX_STEP_KCAL)
                    adjustment = (adjustment + step).coerceIn(-MAX_ADJUSTMENT_KCAL, MAX_ADJUSTMENT_KCAL)
                }
            }
            steps += AdjustmentStep(weekly[i].date, adjustment)
        }
        return steps
    }
}

internal enum class Direction { LOSE, GAIN, MAINTAIN }

/** The learned correction right after the weigh-in on [date]. */
internal data class AdjustmentStep(val date: LocalDate, val adjustmentKcal: Int)
