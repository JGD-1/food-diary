package nl.guido.foodtracker.feature.progress.stats

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class StatsMathTest {

    /** Friday 9 October 2026; the week is Monday 5 to Sunday 11 October. */
    private val today = LocalDate.of(2026, 10, 9)

    private var counter = 0

    private fun entry(date: LocalDate, kcal: Double, meal: Meal = Meal.LUNCH, name: String = "Food") = LogEntry(
        id = "e${counter++}", userId = "u", date = date, meal = meal,
        what = Logged.FoodRef("f", name), portion = Portion(100.0),
        nutrients = Nutrients(kcal, 0.0, 0.0, 0.0), isEstimate = false, createdAt = Instant.EPOCH,
    )

    @Test
    fun `the week runs Monday to Sunday and marks today`() {
        val week = StatsMath.week(emptyList(), today)
        assertEquals(LocalDate.of(2026, 10, 5), week.from)
        assertEquals(LocalDate.of(2026, 10, 11), week.to)
        assertEquals(listOf(DayKind.PAST, DayKind.PAST, DayKind.PAST, DayKind.PAST, DayKind.TODAY, DayKind.FUTURE, DayKind.FUTURE), week.days.map { it.kind })
        assertNull(week.averageKcal)
    }

    @Test
    fun `average counts finished logged days only, not today or empty days`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 5), 2000.0),
            entry(LocalDate.of(2026, 10, 5), 200.0),
            entry(LocalDate.of(2026, 10, 7), 1800.0),
            entry(today, 900.0), // still being filled in
            entry(LocalDate.of(2026, 10, 4), 5000.0), // last week
        )
        val week = StatsMath.week(entries, today)
        assertEquals(2000.0, week.averageKcal!!, 1e-9)
        assertEquals(2200.0, week.days[0].kcal, 1e-9)
        assertEquals(false, week.days[1].logged)
        assertEquals(900.0, week.days[4].kcal, 1e-9)
    }

    @Test
    fun `on the first logged day the average is today so far`() {
        val week = StatsMath.week(listOf(entry(today, 700.0)), today)
        assertEquals(700.0, week.averageKcal!!, 1e-9)
    }

    @Test
    fun `drinks share and the drinks with the most calories`() {
        val day = LocalDate.of(2026, 10, 6)
        val entries = listOf(
            entry(day, 1500.0),
            entry(day, 215.0, Meal.DRINKS, "Beer"),
            entry(day, 215.0, Meal.DRINKS, "Beer"),
            entry(day, 140.0, Meal.DRINKS, "Cola"),
            entry(day, 30.0, Meal.DRINKS, "Coffee with milk"),
        )
        val week = StatsMath.week(entries, today)
        assertEquals(600.0, week.drinksKcal, 1e-9)
        assertEquals(29, week.drinksPercent) // 600 / 2100
        assertEquals(listOf("Beer", "Cola"), week.topDrinks)
    }

    @Test
    fun `no drinks means zero percent and no names`() {
        val week = StatsMath.week(listOf(entry(today, 500.0)), today)
        assertEquals(0, week.drinksPercent)
        assertEquals(emptyList<String>(), week.topDrinks)
    }

    @Test
    fun `six months, oldest first, averaged per logged day`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 9, 1), 2000.0),
            entry(LocalDate.of(2026, 9, 30), 2200.0),
            entry(LocalDate.of(2026, 5, 15), 2400.0),
            entry(LocalDate.of(2026, 10, 1), 1900.0),
            entry(today, 500.0),
        )
        val months = StatsMath.months(entries, today)
        assertEquals((5..10).map { YearMonth.of(2026, it) }, months.map { it.month })
        val sep = months[4]
        assertEquals(2100.0, sep.averageKcal!!, 1e-9)
        assertEquals(2, sep.daysLogged)
        assertEquals(30, sep.daysSoFar)
        assertNull(months[1].averageKcal) // June: nothing logged
        val oct = months[5]
        assertEquals(1900.0, oct.averageKcal!!, 1e-9) // today left out while it's being filled in
        assertEquals(9, oct.daysSoFar)
        assertEquals(true, oct.isCurrent)
    }

    @Test
    fun `entries are loaded from the oldest month shown`() {
        assertEquals(LocalDate.of(2026, 5, 1), StatsMath.firstDayNeeded(today))
    }
}
