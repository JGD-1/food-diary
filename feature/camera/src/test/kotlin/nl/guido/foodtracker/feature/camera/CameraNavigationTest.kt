package nl.guido.foodtracker.feature.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraNavigationTest {
    @Test
    fun `Log food is recognised with and without its extras`() {
        assertTrue(isLogFoodRoute("log-food"))
        assertTrue(isLogFoodRoute("log-food?meal={meal}&foodId={foodId}&grams={grams}"))
    }

    @Test
    fun `other screens are not Log food`() {
        assertFalse(isLogFoodRoute(null))
        assertFalse(isLogFoodRoute("today"))
        assertFalse(isLogFoodRoute("log-food-extra"))
    }

    @Test
    fun `only Today and Log food are offered a named food when weighing`() {
        assertTrue(logsFoodItself("today"))
        assertTrue(logsFoodItself("day/{date}"))
        assertTrue(logsFoodItself("log-food?meal={meal}&foodId={foodId}&grams={grams}"))
        assertFalse(logsFoodItself("recipes/batch-cook"))
        assertFalse(logsFoodItself(null))
    }
}
