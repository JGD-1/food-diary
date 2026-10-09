package nl.guido.foodtracker.feature.progress.weight

import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class WeightMathTest {

    private fun profile(start: Double = 80.1, target: Double = 75.0) = UserProfile(
        id = "u", name = "Me", birthYear = 1985, sex = Sex.MALE, heightCm = 180,
        activity = ActivityLevel.LIGHT, startWeightKg = start, targetWeightKg = target, weeklyPaceKg = 0.25,
    )

    private fun weighIn(date: LocalDate, kg: Double) = WeighIn(id = "$date", userId = "u", date = date, kg = kg)

    /** Weekly Sunday weigh-ins starting Sunday 19 July 2026. */
    private fun sundays(vararg kgs: Double): List<WeighIn> {
        val first = LocalDate.of(2026, 7, 19)
        return kgs.mapIndexed { i, kg -> weighIn(first.plusWeeks(i.toLong()), kg) }
    }

    @Test
    fun `one point per week, the last weigh-in of that week`() {
        val monday = LocalDate.of(2026, 10, 5)
        val points = WeightMath.weeklyPoints(
            listOf(
                weighIn(monday.plusDays(6), 78.4), // Sunday
                weighIn(monday.plusDays(2), 78.9), // Wednesday, same week
                weighIn(monday.minusDays(1), 78.8), // previous Sunday
            ),
        )
        assertEquals(2, points.size)
        assertEquals(78.8, points[0].kg, 1e-9)
        assertEquals(78.4, points[1].kg, 1e-9)
        assertEquals(monday, points[1].weekStart)
    }

    @Test
    fun `progress from start to target when losing`() {
        val points = WeightMath.weeklyPoints(sundays(80.1, 79.5, 78.4))
        val p = WeightMath.progress(profile(), points)
        assertEquals(78.4, p.currentKg, 1e-9)
        assertEquals(3.4, p.toGoKg, 1e-9)
        assertEquals(1.7, p.movedKg, 1e-9)
        assertEquals(1.7 / 5.1, p.fraction, 1e-9)
        assertEquals(LocalDate.of(2026, 7, 19), p.startDate)
        assertFalse(p.reached)
    }

    @Test
    fun `progress works when the target is above the start`() {
        val points = WeightMath.weeklyPoints(sundays(60.0, 61.0))
        val p = WeightMath.progress(profile(start = 60.0, target = 64.0), points)
        assertEquals(3.0, p.toGoKg, 1e-9)
        assertEquals(0.25, p.fraction, 1e-9)
    }

    @Test
    fun `going the other way never makes the bar negative`() {
        val points = WeightMath.weeklyPoints(sundays(81.0))
        val p = WeightMath.progress(profile(), points)
        assertEquals(0.0, p.fraction, 1e-9)
        assertEquals(-0.9, p.movedKg, 1e-9)
    }

    @Test
    fun `reaching or passing the target counts as reached`() {
        val p = WeightMath.progress(profile(), WeightMath.weeklyPoints(sundays(80.0, 74.8)))
        assertTrue(p.reached)
        assertEquals(1.0, p.fraction, 1e-9)
        assertEquals(0.0, p.toGoKg, 1e-9)
    }

    @Test
    fun `no weigh-ins yet uses the start weight`() {
        val p = WeightMath.progress(profile(), emptyList())
        assertEquals(80.1, p.currentKg, 1e-9)
        assertNull(p.startDate)
    }

    @Test
    fun `week to week changes know which way is toward the target`() {
        val points = WeightMath.weeklyPoints(sundays(80.0, 79.6, 79.8))
        val changes = WeightMath.changes(points, 80.1, 75.0)
        assertEquals(-0.4, changes[0].deltaKg, 1e-9)
        assertTrue(changes[0].towardTarget)
        assertEquals(0.2, changes[1].deltaKg, 1e-9)
        assertFalse(changes[1].towardTarget)
    }

    @Test
    fun `steady weekly loss gives the rate and the month the target is reached`() {
        // 0.5 kg a week from 80.0; last weigh-in 77.5 on 23 Aug; 2.5 kg to go = 5 weeks = 27 Sep.
        val summary = WeightMath.summary(profile(target = 75.0), sundays(80.0, 79.5, 79.0, 78.5, 78.0, 77.5))
        val trend = summary.trend as TrendNote.Toward
        assertEquals(0.5, trend.kgPerWeek, 1e-9)
        assertEquals(YearMonth.of(2026, 9), trend.eta)
    }

    @Test
    fun `the trend looks only at the last twelve weeks`() {
        // A big drop long ago, then 0.1 kg a week for the last 13 weeks.
        val kgs = doubleArrayOf(90.0, 85.0) + DoubleArray(13) { 80.0 - it * 0.1 }
        val points = WeightMath.weeklyPoints(sundays(*kgs))
        assertEquals(-0.1, WeightMath.kgPerWeek(points)!!, 1e-9)
    }

    @Test
    fun `fewer than three weigh-ins asks for more`() {
        assertEquals(TrendNote.NotEnoughData(3), WeightMath.summary(profile(), emptyList()).trend)
        assertEquals(TrendNote.NotEnoughData(1), WeightMath.summary(profile(), sundays(80.0, 79.8)).trend)
    }

    @Test
    fun `almost no change reads as steady`() {
        assertEquals(TrendNote.Steady, WeightMath.summary(profile(), sundays(79.0, 79.1, 79.0, 79.0)).trend)
    }

    @Test
    fun `a target years away reads as steady, not as a far-off date`() {
        // 0.06 kg a week with 4 kg to go is over 60 weeks... fine; with 20 kg to go it's 6 years.
        val trend = WeightMath.summary(profile(start = 100.0, target = 75.0), sundays(95.18, 95.12, 95.06, 95.0)).trend
        assertEquals(TrendNote.Steady, trend)
    }

    @Test
    fun `moving away from the target is described neutrally`() {
        val trend = WeightMath.summary(profile(), sundays(78.0, 78.3, 78.6))
            .trend as TrendNote.Away
        assertEquals(0.3, trend.kgPerWeek, 1e-9)
    }

    @Test
    fun `summary keeps the weekday of the latest weigh-in`() {
        assertEquals(DayOfWeek.SUNDAY, WeightMath.summary(profile(), sundays(80.0)).weighInDay)
    }
}
