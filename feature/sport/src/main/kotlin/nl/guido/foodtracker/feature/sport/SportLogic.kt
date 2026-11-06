package nl.guido.foodtracker.feature.sport

import nl.guido.foodtracker.core.model.SportEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How often, how long and how many kcal: for one day or for this week. */
internal data class SportSummary(val times: Int, val minutes: Int, val kcal: Int) {
    companion object {
        fun of(entries: List<SportEntry>) = SportSummary(entries.size, entries.sumOf { it.minutes ?: 0 }, entries.sumOf { it.kcal })
    }
}

/** What the Sport tab shows: today's lines, this week's summary, and earlier lines (newest first). */
internal data class SportDays(
    val today: List<SportEntry>,
    val todaySummary: SportSummary,
    val week: SportSummary,
    val earlier: List<SportEntry>,
)

internal object SportLogic {
    /** Days shown under "Earlier". */
    const val EARLIER_DAYS = 14L

    /** Weeks start on Monday, as in Stats. */
    fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun firstDayLoaded(today: LocalDate): LocalDate = minOf(weekStart(today), today.minusDays(EARLIER_DAYS))

    fun days(entries: List<SportEntry>, today: LocalDate): SportDays {
        val todays = entries.filter { it.date == today }.sortedBy { it.createdAt }
        val week = entries.filter { !it.date.isBefore(weekStart(today)) && !it.date.isAfter(today) }
        val earlier = entries
            .filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(EARLIER_DAYS)) }
            .sortedWith(compareByDescending<SportEntry> { it.date }.thenByDescending { it.createdAt })
        return SportDays(todays, SportSummary.of(todays), SportSummary.of(week), earlier)
    }
}

/** What was typed in the add/change form, checked. */
internal data class SportForm(val name: String = "", val minutes: String = "", val kcal: String = "") {
    val cleanName: String get() = name.trim()

    /** Whole kcal from 1 to [SportEntry.MAX_KCAL]; null when empty or out of range. */
    val kcalValue: Int? get() = kcal.trim().toIntOrNull()?.takeIf { it in 1..SportEntry.MAX_KCAL }

    /** Optional: null when empty. */
    val minutesValue: Int? get() = minutes.trim().toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }

    val minutesOk: Boolean get() = minutes.isBlank() || minutesValue != null
    val kcalTyped: Boolean get() = kcal.isNotBlank()
    val canSave: Boolean get() = cleanName.isNotEmpty() && kcalValue != null && minutesOk

    companion object {
        const val MAX_MINUTES = 24 * 60

        fun of(entry: SportEntry) = SportForm(entry.name, entry.minutes?.toString().orEmpty(), entry.kcal.toString())
    }
}

/** "1 h 10 min" / "45 min" parts, so the screen can put them in its own words. */
internal fun hoursAndMinutes(minutes: Int): Pair<Int, Int> = minutes / 60 to minutes % 60
