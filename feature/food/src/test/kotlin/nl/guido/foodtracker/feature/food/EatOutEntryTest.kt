package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.feature.food.ui.EatOutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EatOutEntryTest {
    @Test fun `meal follows the time of day`() {
        assertEquals(Meal.BREAKFAST, EatOutViewModel.mealFor(LocalTime.of(9, 30)))
        assertEquals(Meal.LUNCH, EatOutViewModel.mealFor(LocalTime.of(12, 0)))
        assertEquals(Meal.DINNER, EatOutViewModel.mealFor(LocalTime.of(19, 0)))
        assertEquals(Meal.DINNER, EatOutViewModel.mealFor(LocalTime.of(23, 30)))
    }

    @Test fun `entry is marked as estimate, keeps the range and counts the typical value`() {
        val clock = Clock.fixed(Instant.parse("2026-10-09T18:00:00Z"), ZoneId.of("Europe/Amsterdam"))
        val entry = EatOutViewModel.buildEntry("u1", "Pizza margherita", Meal.DINNER, Estimate(450, 560, 680), clock)
        assertTrue(entry.isEstimate)
        assertEquals(LocalDate.of(2026, 10, 9), entry.date)
        assertEquals(560.0, entry.nutrients.kcal, 0.0)
        assertEquals(Logged.Restaurant("Pizza margherita", Estimate(450, 560, 680)), entry.what)
        assertEquals("u1", entry.userId)
    }
}
