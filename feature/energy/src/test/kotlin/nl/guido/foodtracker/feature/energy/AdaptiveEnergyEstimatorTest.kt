package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEnergyEstimatorTest {
    private val estimator = AdaptiveEnergyEstimator { MONDAY }

    @Test
    fun targetIsMaintenanceMinusPace() {
        val result = estimator.dailyTarget(PROFILE, emptyList(), emptyList())
        // Age 36: 10 × 85 + 6.25 × 180 − 180 + 5 = 1,800; × 1.375 = 2,475; − 550 = 1,925
        assertEquals(1800, result.bmrKcal)
        assertEquals(2475, result.maintenanceKcal)
        assertEquals(550, result.deficitKcal)
        assertEquals(0, result.adjustmentKcal)
        assertEquals(1925, result.targetKcal)
        assertFalse(result.isManual)
    }

    @Test
    fun usesTheLatestWeighIn() {
        val result = estimator.details(PROFILE, listOf(weighIn(MONDAY, 82.0)), emptyList())
        assertEquals(82.0, result.weightKg, 0.0)
        assertEquals(1770, result.breakdown.bmrKcal)
    }

    @Test
    fun manualTargetWinsButCalculationIsKept() {
        val result = estimator.details(PROFILE.copy(manualTargetKcal = 2000), emptyList(), emptyList())
        assertEquals(2000, result.breakdown.targetKcal)
        assertTrue(result.breakdown.isManual)
        assertEquals(1925, result.calculatedTargetKcal)
    }

    @Test
    fun atTargetWeightTheAimIsMaintenance() {
        val result = estimator.details(PROFILE.copy(targetWeightKg = 85.2), emptyList(), emptyList())
        assertEquals(Direction.MAINTAIN, result.direction)
        assertEquals(0, result.breakdown.deficitKcal)
        assertEquals(result.breakdown.maintenanceKcal, result.breakdown.targetKcal)
    }

    @Test
    fun gainingAddsThePace() {
        val result = estimator.details(PROFILE.copy(targetWeightKg = 90.0), emptyList(), emptyList())
        assertEquals(-550, result.breakdown.deficitKcal)
        assertEquals(2475 + 550, result.breakdown.targetKcal)
    }

    @Test
    fun neverBelowTheMinimum() {
        val small = PROFILE.copy(sex = Sex.FEMALE, heightCm = 150, startWeightKg = 50.0, targetWeightKg = 45.0,
            activity = ActivityLevel.SEDENTARY, weeklyPaceKg = 1.0)
        val result = estimator.details(small, emptyList(), emptyList())
        assertEquals(EnergyMath.MIN_TARGET_KCAL, result.breakdown.targetKcal)
        assertTrue(result.keptAtMinimum)
    }

    @Test
    fun countsWeighInsUntilLearningStarts() {
        assertEquals(3, estimator.details(PROFILE, emptyList(), emptyList()).weighInsUntilLearning)
        assertEquals(1, estimator.details(PROFILE, weeklyWeighIns(85.0, 84.6), emptyList()).weighInsUntilLearning)
        assertEquals(0, estimator.details(PROFILE, weeklyWeighIns(85.0, 84.6, 84.2), emptyList()).weighInsUntilLearning)
    }

    @Test
    fun learnedAdjustmentIsPartOfTheTarget() {
        val weighIns = weeklyWeighIns(85.0, 85.0, 85.0)
        val intake = dailyIntake(MONDAY.minusWeeks(2), MONDAY, 3000.0)
        val result = estimator.dailyTarget(PROFILE, weighIns, intake)
        assertEquals(100, result.adjustmentKcal)
        assertEquals(result.maintenanceKcal - result.deficitKcal + 100, result.targetKcal)
    }
}
