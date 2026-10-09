package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.ReviewSuggestion
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import nl.guido.foodtracker.core.model.WeeklyReview
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Builds the Monday review (core.model.WeeklyReview); stream 5 shows it as a card on Today. */
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
