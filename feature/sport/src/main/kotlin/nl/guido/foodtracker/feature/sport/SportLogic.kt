package nl.guido.foodtracker.feature.sport

import nl.guido.foodtracker.core.model.SportEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

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

/**
 * Round 17 (P11): kinds of sport with a typical effort (MET, from the Compendium of Physical Activities),
 * so the kcal can be estimated from the kind and the minutes instead of typed from memory.
 */
internal enum class SportKind(val label: Int, val met: Double) {
    WALKING(R.string.sport_type_walking, 3.5),
    CYCLING(R.string.sport_type_cycling, 7.5),
    RUNNING(R.string.sport_type_running, 9.8),
    SWIMMING(R.string.sport_type_swimming, 7.0),
    STRENGTH(R.string.sport_type_strength, 5.0),
    YOGA(R.string.sport_type_yoga, 2.5),
}

internal object SportEstimate {
    /** Used until the profile has a weight. */
    const val DEFAULT_KG = 75.0

    /** For a sport typed by hand that isn't one of the kinds: moderate effort. */
    const val DEFAULT_MET = 5.0

    /** kcal ≈ MET × body weight (kg) × hours; null without minutes. */
    fun kcal(met: Double, minutes: Int?, kg: Double?): Int? {
        if (minutes == null || minutes <= 0) return null
        val weight = kg?.takeIf { it in 30.0..300.0 } ?: DEFAULT_KG
        return (met * weight * minutes / 60.0).roundToInt().coerceIn(1, SportEntry.MAX_KCAL)
    }
}
