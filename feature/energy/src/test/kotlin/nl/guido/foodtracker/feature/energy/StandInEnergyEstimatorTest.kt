package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandInEnergyEstimatorTest {
    private val profile = UserProfile(
        id = "u", name = "Test", birthYear = 1990, sex = Sex.MALE, heightCm = 180,
        activity = ActivityLevel.LIGHT, startWeightKg = 85.0, targetWeightKg = 78.0, weeklyPaceKg = 0.5,
    )

    @Test
    fun targetIsMaintenanceMinusDeficit() {
        val result = StandInEnergyEstimator().dailyTarget(profile, emptyList(), emptyList())
        assertEquals(result.maintenanceKcal - result.deficitKcal, result.targetKcal)
        assertTrue(result.targetKcal in 1500..3000)
    }

    @Test
    fun manualTargetWins() {
        val result = StandInEnergyEstimator().dailyTarget(profile.copy(manualTargetKcal = 2000), emptyList(), emptyList())
        assertEquals(2000, result.targetKcal)
        assertTrue(result.isManual)
    }
}
