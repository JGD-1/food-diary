package nl.guido.foodtracker.feature.sport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportEstimateTest {
    @Test
    fun `half an hour of running at 70 kg`() {
        // 9.8 MET x 70 kg x 0.5 h = 343 kcal
        assertEquals(343, SportEstimate.kcal(SportKind.RUNNING.met, 30, 70.0))
    }

    @Test
    fun `no minutes, no estimate`() {
        assertNull(SportEstimate.kcal(SportKind.WALKING.met, null, 70.0))
        assertNull(SportEstimate.kcal(SportKind.WALKING.met, 0, 70.0))
    }

    @Test
    fun `without a weight the default is used`() {
        // 3.5 x 75 x 1 h
        assertEquals(263, SportEstimate.kcal(SportKind.WALKING.met, 60, null))
    }

    @Test
    fun `a very long session is capped`() {
        assertEquals(3000, SportEstimate.kcal(SportKind.RUNNING.met, 600, 120.0))
    }
}
