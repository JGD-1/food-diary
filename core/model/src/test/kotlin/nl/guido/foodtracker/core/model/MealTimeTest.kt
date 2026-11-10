package nl.guido.foodtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealTimeTest {
    @Test
    fun `one rule for every screen`() {
        assertEquals(Meal.BREAKFAST, mealForTime(LocalTime.of(7, 30)))
        assertEquals(Meal.LUNCH, mealForTime(LocalTime.of(10, 30)))
        assertEquals(Meal.LUNCH, mealForTime(LocalTime.of(14, 59)))
        // 16:01 is dinner time everywhere now (Koken used to say snacks).
        assertEquals(Meal.DINNER, mealForTime(LocalTime.of(16, 1)))
        // A late dinner at 21:15 stays dinner.
        assertEquals(Meal.DINNER, mealForTime(LocalTime.of(21, 15)))
        assertEquals(Meal.SNACKS, mealForTime(LocalTime.of(22, 0)))
    }
}
