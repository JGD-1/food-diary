package nl.guido.foodtracker.feature.camera

import nl.guido.foodtracker.core.model.Meal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TargetMealTest {
    @Test
    fun `a drink always goes to drinks`() {
        assertEquals(Meal.DRINKS, targetMeal(isDrink = true, openerMeal = Meal.LUNCH, now = LocalTime.NOON))
    }

    @Test
    fun `the opener's meal wins over the clock`() {
        assertEquals(Meal.BREAKFAST, targetMeal(isDrink = false, openerMeal = Meal.BREAKFAST, now = LocalTime.of(19, 0)))
    }

    @Test
    fun `without an opener meal the shared clock decides, dinner until 22`() {
        assertEquals(Meal.DINNER, targetMeal(isDrink = false, openerMeal = null, now = LocalTime.of(21, 15)))
    }
}
