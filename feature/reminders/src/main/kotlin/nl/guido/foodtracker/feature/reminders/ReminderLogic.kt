package nl.guido.foodtracker.feature.reminders

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** The one reminder: on or off, and the time of day it shows. Kept on this phone only. */
data class ReminderSettings(val enabled: Boolean = false, val time: LocalTime = DEFAULT_TIME) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(12, 30)
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
