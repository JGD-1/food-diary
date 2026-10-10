package nl.guido.foodtracker.feature.progress.stats

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

internal enum class DayKind { PAST, TODAY, FUTURE }

/** One day of this week. [kcal] is 0 for a day with nothing logged. */
internal data class DayBar(val date: LocalDate, val kcal: Double, val logged: Boolean, val kind: DayKind)

internal data class WeekStats(
    val from: LocalDate,
    val to: LocalDate,
    /** Monday to Sunday. */
    val days: List<DayBar>,
    /** Average kcal per logged day, or null when nothing is logged yet. See [StatsMath.averagePerLoggedDay]. */
    val averageKcal: Double?,
    /** Everything logged this week so far, today included. */
    val totalKcal: Double,
    val drinksKcal: Double,
    /** Share of this week's kcal that came from drinks, 0 to 100. */
    val drinksPercent: Int,
    /** The drinks with the most kcal this week, most first, at most two. */
    val topDrinks: List<String>,
)

internal data class MonthAverage(
    val month: YearMonth,
    /** Average kcal per logged day, or null when nothing was logged that month. */
    val averageKcal: Double?,
    val daysLogged: Int,
    /** Days in the month so far (all of them for a finished month). */
    val daysSoFar: Int,
    val isCurrent: Boolean,
)

internal const val MONTHS_SHOWN = 6

internal object StatsMath {

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** First day the Stats tab needs entries from: the start of the oldest month shown, or of the week shown. */
    fun firstDayNeeded(today: LocalDate, weeksBack: Int = 0): LocalDate =
        minOf(YearMonth.from(today).minusMonths((MONTHS_SHOWN - 1).toLong()).atDay(1), weekStart(today).minusWeeks(weeksBack.toLong()))

    fun dayTotals(entries: List<LogEntry>): Map<LocalDate, Double> =
        entries.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.nutrients.kcal } }

    /**
     * Average over the days that have something logged, so a day you didn't log doesn't count as
     * "ate nothing". Today is left out while it's still being filled in, unless it's the only day.
     */
    fun averagePerLoggedDay(totals: Map<LocalDate, Double>, days: Iterable<LocalDate>, today: LocalDate): Double? {
        val logged = days.filter { it in totals && it <= today }
        val finished = logged.filter { it < today }
        val counted = finished.ifEmpty { logged }
        if (counted.isEmpty()) return null
        return counted.sumOf { totals.getValue(it) } / counted.size
    }

    /** The week's budget: seven times the daily target, so a big Saturday can balance out over the week. */
    fun weekBudget(dailyTargetKcal: Int): Double = 7.0 * dailyTargetKcal

    /** The week [weeksBack] weeks before this one (0 = this week), Monday to Sunday. */
    fun week(entries: List<LogEntry>, today: LocalDate, weeksBack: Int = 0): WeekStats {
        val from = weekStart(today).minusWeeks(weeksBack.toLong())
        val to = from.plusDays(6)
        val inWeek = entries.filter { it.date in from..to }
        val totals = dayTotals(inWeek)
        val dates = (0L..6L).map { from.plusDays(it) }
        val days = dates.map { date ->
            DayBar(
                date = date,
                kcal = totals[date] ?: 0.0,
                logged = date in totals,
                kind = when {
                    date < today -> DayKind.PAST
                    date == today -> DayKind.TODAY
                    else -> DayKind.FUTURE
                },
            )
        }
        val drinks = inWeek.filter { it.meal == Meal.DRINKS }
        val drinksKcal = drinks.sumOf { it.nutrients.kcal }
        val allKcal = inWeek.sumOf { it.nutrients.kcal }
        val topDrinks = drinks
            .groupBy { it.what.displayName() }
            .mapValues { (_, list) -> list.sumOf { it.nutrients.kcal } }
            .entries
            .sortedByDescending { it.value }
            .filter { it.value > 0 }
            .take(2)
            .map { it.key }
        return WeekStats(
            from = from,
            to = to,
            days = days,
            averageKcal = averagePerLoggedDay(totals, dates, today),
            totalKcal = allKcal,
            drinksKcal = drinksKcal,
            drinksPercent = if (allKcal > 0) Math.round(drinksKcal / allKcal * 100).toInt() else 0,
            topDrinks = topDrinks,
        )
    }

    /** The last [MONTHS_SHOWN] months including this one, oldest first. */
    fun months(entries: List<LogEntry>, today: LocalDate): List<MonthAverage> {
        val totals = dayTotals(entries)
        val current = YearMonth.from(today)
        return (MONTHS_SHOWN - 1 downTo 0).map { back ->
            val month = current.minusMonths(back.toLong())
            val lastDay = if (month == current) today else month.atEndOfMonth()
            val dates = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { it <= lastDay }.toList()
            MonthAverage(
                month = month,
                averageKcal = averagePerLoggedDay(totals, dates, today),
                daysLogged = dates.count { it in totals },
                daysSoFar = dates.size,
                isCurrent = month == current,
            )
        }
    }
}

internal fun Logged.displayName(): String = when (this) {
    is Logged.FoodRef -> name
    is Logged.RecipeRef -> name
    is Logged.BatchShare -> name
    is Logged.Restaurant -> dish
}
