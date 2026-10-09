package nl.guido.foodtracker.core.model

import java.time.LocalDate

/**
 * The Monday review: last week's average, the weigh-in result, and one suggestion.
 * Built by stream 4 (feature/energy); stream 5 shows it as a card on Today.
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
