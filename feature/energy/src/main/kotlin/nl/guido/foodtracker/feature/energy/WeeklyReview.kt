package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Monday review: last week's average, the weigh-in result, and one suggestion.
 * Stream 5 shows it as a card on Today.
 */
data class WeeklyReview(
    /** Monday of the week being reviewed. */
    val weekStart: LocalDate,
    /** Average kcal over the days that had food logged, or null when nothing was logged. */
    val averageKcal: Int?,
    val daysLogged: Int,
    val targetKcal: Int,
    /** The latest weigh-in from last Monday up to today (a Monday-morning weigh-in counts). */
    val weighInKg: Double?,
    /** Change since the weekly weigh-in before it (negative = lighter). Null without two weigh-ins. */
    val changeKg: Double?,
    val targetWeightKg: Double,
    val suggestion: ReviewSuggestion,
)

/** One suggestion for the week ahead. Supportive and neutral; the texts live with the card. */
enum class ReviewSuggestion {
    /** At (or past) the target weight: maybe switch to a maintenance target. */
    GOAL_REACHED,

    /** Fewer than 4 days logged: logging most days makes the target more accurate. */
    LOG_MORE_DAYS,

    /** No weigh-in since last Monday: a weigh-in this week shows the trend. */
    WEIGH_IN,

    /** Average more than 10% over target: planning one meal ahead can help. */
    PLAN_AHEAD,

    /** Average more than 15% under target: eating enough helps you keep going. */
    EAT_ENOUGH,

    /** Close to target: keep the same rhythm. */
    KEEP_GOING,
}

internal object WeeklyReviews {
    const val MIN_DAYS_LOGGED = 4
    const val ABOVE_SHARE = 1.10
    const val BELOW_SHARE = 0.85

    fun isReviewDay(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY

    /** The review of the week before [today]'s week. Works on any day; the card shows it on Mondays. */
    fun review(today: LocalDate, profile: UserProfile, weighIns: List<WeighIn>, intake: List<DayTotal>, targetKcal: Int): WeeklyReview {
        val thisWeek = EnergyMath.weekStart(today)
        val lastWeek = thisWeek.minusDays(7)
        val days = intake.filter { it.date >= lastWeek && it.date < thisWeek && it.kcal > 0 }
        val average = days.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()?.roundToInt()

        val weekly = EnergyMath.weeklyWeighIns(weighIns.filter { !it.date.isAfter(today) })
        val latest = weekly.lastOrNull()?.takeIf { !it.date.isBefore(lastWeek) }
        val previous = latest?.let { weekly.getOrNull(weekly.size - 2) }
        val change = if (latest != null && previous != null) latest.kg - previous.kg else null

        val currentKg = weekly.lastOrNull()?.kg ?: profile.startWeightKg
        val suggestion = when {
            goalReached(profile, currentKg) -> ReviewSuggestion.GOAL_REACHED
            days.size < MIN_DAYS_LOGGED -> ReviewSuggestion.LOG_MORE_DAYS
            latest == null -> ReviewSuggestion.WEIGH_IN
            average != null && average > targetKcal * ABOVE_SHARE -> ReviewSuggestion.PLAN_AHEAD
            average != null && average < targetKcal * BELOW_SHARE -> ReviewSuggestion.EAT_ENOUGH
            else -> ReviewSuggestion.KEEP_GOING
        }
        return WeeklyReview(
            weekStart = lastWeek,
            averageKcal = average,
            daysLogged = days.size,
            targetKcal = targetKcal,
            weighInKg = latest?.kg,
            changeKg = change,
            targetWeightKg = profile.targetWeightKg,
            suggestion = suggestion,
        )
    }

    /** Reached when within the margin, or past the target in the direction they were heading. */
    private fun goalReached(profile: UserProfile, currentKg: Double): Boolean {
        val losing = profile.startWeightKg > profile.targetWeightKg
        return abs(currentKg - profile.targetWeightKg) <= EnergyMath.AT_TARGET_MARGIN_KG ||
            (losing && currentKg < profile.targetWeightKg) ||
            (!losing && currentKg > profile.targetWeightKg)
    }
}

/** Diary lines → kcal per day (only days with something logged). Estimates count with their typical value. */
internal fun dayTotals(entries: List<LogEntry>): List<DayTotal> =
    entries.groupBy { it.date }
        .map { (date, list) -> DayTotal(date, list.sumOf { it.nutrients.kcal }) }
        .sortedBy { it.date }
