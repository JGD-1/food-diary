package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.WeighIn
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** The one suggestion on the Monday card. Always an easy next step, never a judgement. */
internal enum class Suggestion { SET_TARGET, LOG_MORE_DAYS, WEIGH_IN, ABOVE_TARGET, WELL_BELOW_TARGET, KEEP_GOING }

/** The Monday review of last week (Monday to Sunday). */
internal data class WeeklyReview(
    /** The Monday this review is shown on; used to remember that it was put away. */
    val shownOn: LocalDate,
    val daysLogged: Int,
    /** Average kcal over the days that have something logged. */
    val averageKcal: Int?,
    val targetKcal: Int?,
    val latestWeighIn: WeighIn?,
    /** Change since the weigh-in before it; null when there is only one. */
    val weighInChangeKg: Double?,
    val suggestion: Suggestion,
)

/**
 * The review for [today], or null when it isn't Monday or there is nothing to look back on.
 * [entries] and [weighIns] may cover more days; only last week (and a weigh-in this morning) count.
 */
internal fun weeklyReview(
    today: LocalDate,
    entries: List<LogEntry>,
    weighIns: List<WeighIn>,
    targetKcal: Int?,
): WeeklyReview? {
    if (today.dayOfWeek != DayOfWeek.MONDAY) return null
    val from = today.minusDays(7)
    val to = today.minusDays(1)
    val perDay = entries.filter { it.date in from..to }
        .groupBy { it.date }
        .mapValues { (_, list) -> list.sumOf { it.nutrients.kcal } }
    val average = if (perDay.isEmpty()) null else (perDay.values.sum() / perDay.size).roundToInt()

    val sorted = weighIns.filter { it.date <= today }.sortedBy { it.date }
    val latest = sorted.lastOrNull()?.takeIf { it.date >= from }
    val previous = latest?.let { l -> sorted.lastOrNull { it.date < l.date } }
    val change = if (latest != null && previous != null) ((latest.kg - previous.kg) * 10).roundToInt() / 10.0 else null

    if (perDay.isEmpty() && latest == null) return null

    val suggestion = when {
        targetKcal == null -> Suggestion.SET_TARGET
        perDay.size < 4 -> Suggestion.LOG_MORE_DAYS
        latest == null -> Suggestion.WEIGH_IN
        average!! > targetKcal + 150 -> Suggestion.ABOVE_TARGET
        average!! < targetKcal - 300 -> Suggestion.WELL_BELOW_TARGET
        else -> Suggestion.KEEP_GOING
    }
    return WeeklyReview(today, perDay.size, average, targetKcal, latest, change, suggestion)
}
