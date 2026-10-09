package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.WeeklyReview
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The review card to show on [today], or null. The energy part builds the review of last week;
 * the card shows from Monday through Sunday of the week after, until it is put away with "Got it".
 * [dismissedWeek] is the weekStart (ISO date) of the review that was put away.
 */
internal fun reviewToShow(today: LocalDate, review: WeeklyReview?, dismissedWeek: String?): WeeklyReview? {
    review ?: return null
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    if (review.weekStart != thisMonday.minusWeeks(1)) return null
    if (review.weekStart.toString() == dismissedWeek) return null
    if (review.daysLogged == 0 && review.weighInKg == null) return null
    return review
}
