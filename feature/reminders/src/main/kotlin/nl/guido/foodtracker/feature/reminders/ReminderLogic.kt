package nl.guido.foodtracker.feature.reminders

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The reminder: on or off, the time of day it shows, and an optional second time (two at most).
 * Kept on this phone only.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
    val secondTime: LocalTime? = null,
) {
    /** The time for reminder [slot] (0 = first, 1 = second), or null when that slot is not set. */
    fun timeFor(slot: Int): LocalTime? = when (slot) {
        0 -> time
        1 -> secondTime
        else -> null
    }

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(12, 30)
        const val SLOTS = 2

        /** Suggested second time: the evening, or lunch when the first one is already in the evening. */
        fun suggestedSecond(first: LocalTime): LocalTime =
            if (first < LocalTime.of(17, 0)) LocalTime.of(19, 0) else DEFAULT_TIME
    }
}

/** How long until the next [time]: later today, or tomorrow if that moment has passed. */
internal fun delayUntilNext(now: LocalDateTime, time: LocalTime): Duration {
    var next = now.toLocalDate().atTime(time)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

/** The meal the reminder asks about, by its time (same split as Log food and Eat out). */
internal fun mealForTime(time: LocalTime): Meal = when {
    time < LocalTime.of(10, 30) -> Meal.BREAKFAST
    time < LocalTime.of(15, 0) -> Meal.LUNCH
    time < LocalTime.of(21, 0) -> Meal.DINNER
    else -> Meal.SNACKS
}

/** No reminder when that meal already has something in today's diary. */
internal fun shouldRemind(todaysEntries: List<LogEntry>, meal: Meal): Boolean =
    todaysEntries.none { it.meal == meal }
