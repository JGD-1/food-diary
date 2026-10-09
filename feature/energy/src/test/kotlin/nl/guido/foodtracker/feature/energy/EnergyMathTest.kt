package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyMathTest {
    @Test
    fun mifflinStJeorMatchesTheFormula() {
        // 10 × 85 + 6.25 × 180 − 5 × 36 + 5 = 1,800
        assertEquals(1800.0, EnergyMath.restingKcal(85.0, 180, 36, Sex.MALE), 0.001)
        // 10 × 65 + 6.25 × 165 − 5 × 40 − 161 = 1,320.25
        assertEquals(1320.25, EnergyMath.restingKcal(65.0, 165, 40, Sex.FEMALE), 0.001)
    }

    @Test
    fun halfAKiloAWeekIsAbout550KcalADay() {
        assertEquals(550.0, EnergyMath.kcalPerDayForPace(0.5), 0.001)
    }

    @Test
    fun directionUsesHalfAKiloMargin() {
        assertEquals(Direction.LOSE, EnergyMath.direction(80.0, 78.0))
        assertEquals(Direction.GAIN, EnergyMath.direction(60.0, 62.0))
        assertEquals(Direction.MAINTAIN, EnergyMath.direction(78.3, 78.0))
    }

    @Test
    fun weeklyWeighInsKeepsTheLatestPerWeek() {
        val list = listOf(
            weighIn(MONDAY, 85.0),
            weighIn(MONDAY.plusDays(3), 84.6),
            weighIn(MONDAY.plusDays(7), 84.4),
        )
        val weekly = EnergyMath.weeklyWeighIns(list)
        assertEquals(listOf(84.6, 84.4), weekly.map { it.kg })
    }

    @Test
    fun slopeFollowsTheTrend() {
        val points = weeklyWeighIns(85.0, 84.5, 84.0, 83.5)
        assertEquals(-0.5 / 7, EnergyMath.slopeKgPerDay(points), 1e-9)
    }

    @Test
    fun noLearningBeforeTheThirdWeighIn() {
        val weighIns = weeklyWeighIns(85.0, 84.0)
        val intake = dailyIntake(MONDAY.minusWeeks(1), MONDAY, 1500.0)
        assertTrue(EnergyMath.learnAdjustment(PROFILE, weighIns, intake, MONDAY).isEmpty())
    }

    @Test
    fun learningMovesAtMost100KcalAWeek() {
        // Eating 3,000 a day while weight stays flat: the body clearly uses far more than the formula says.
        val weighIns = weeklyWeighIns(85.0, 85.0, 85.0, 85.0, 85.0)
        val intake = dailyIntake(MONDAY.minusWeeks(4), MONDAY, 3000.0)
        val steps = EnergyMath.learnAdjustment(PROFILE, weighIns, intake, MONDAY)
        assertEquals(listOf(100, 200, 300), steps.map { it.adjustmentKcal })
    }

    @Test
    fun learningGoesDownWhenWeightDropsSlowerThanExpected() {
        // Formula maintenance for this profile is about 2,470. Eating 2,300 and weight is flat: real maintenance ≈ 2,300.
        val weighIns = weeklyWeighIns(85.0, 85.0, 85.0, 85.0, 85.0, 85.0, 85.0, 85.0)
        val intake = dailyIntake(MONDAY.minusWeeks(7), MONDAY, 2300.0)
        val steps = EnergyMath.learnAdjustment(PROFILE, weighIns, intake, MONDAY)
        assertTrue(steps.all { it.adjustmentKcal <= 0 })
        assertTrue(steps.zipWithNext().all { (a, b) -> a.adjustmentKcal - b.adjustmentKcal <= 100 })
        // Settles near the real gap (≈ −170) instead of overshooting.
        assertTrue(steps.last().adjustmentKcal in -200..-100)
    }

    @Test
    fun weeksWithLittleLoggingChangeNothing() {
        val weighIns = weeklyWeighIns(85.0, 85.0, 85.0)
        val intake = dailyIntake(MONDAY.minusWeeks(2), MONDAY.minusWeeks(2).plusDays(3), 3000.0)
        val steps = EnergyMath.learnAdjustment(PROFILE, weighIns, intake, MONDAY)
        assertEquals(listOf(0), steps.map { it.adjustmentKcal })
    }

    @Test
    fun adjustmentNeverGoesBeyondTheCap() {
        val weighIns = weeklyWeighIns(*DoubleArray(12) { 85.0 })
        val intake = dailyIntake(MONDAY.minusWeeks(11), MONDAY, 5000.0)
        val steps = EnergyMath.learnAdjustment(PROFILE, weighIns, intake, MONDAY)
        assertEquals(EnergyMath.MAX_ADJUSTMENT_KCAL, steps.last().adjustmentKcal)
    }
}
