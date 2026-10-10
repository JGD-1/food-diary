package nl.guido.foodtracker.feature.reminders

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderLogicTest {
    private val lunchTime = LocalTime.of(12, 30)

    @Test fun laterToday() {
        val now = LocalDateTime.of(2026, 10, 10, 9, 0)
        assertEquals(Duration.ofHours(3).plusMinutes(30), delayUntilNext(now, lunchTime))
    }

    @Test fun alreadyPassedMovesToTomorrow() {
        val now = LocalDateTime.of(2026, 10, 10, 13, 0)
        assertEquals(Duration.ofHours(23).plusMinutes(30), delayUntilNext(now, lunchTime))
    }

    @Test fun exactlyNowMeansTomorrow() {
        val now = LocalDateTime.of(2026, 10, 10, 12, 30)
        assertEquals(Duration.ofDays(1), delayUntilNext(now, lunchTime))
    }

    @Test fun justBeforeMidnight() {
        val now = LocalDateTime.of(2026, 10, 10, 23, 59)
        assertEquals(Duration.ofMinutes(1), delayUntilNext(now, LocalTime.MIDNIGHT))
    }

    @Test fun mealByTime() {
        assertEquals(Meal.BREAKFAST, mealForTime(LocalTime.of(8, 0)))
        assertEquals(Meal.LUNCH, mealForTime(LocalTime.of(10, 30)))
        assertEquals(Meal.LUNCH, mealForTime(lunchTime))
        assertEquals(Meal.DINNER, mealForTime(LocalTime.of(18, 0)))
        assertEquals(Meal.SNACKS, mealForTime(LocalTime.of(21, 30)))
    }

    @Test fun noReminderWhenMealLogged() {
        assertTrue(shouldRemind(emptyList(), Meal.LUNCH))
        assertTrue(shouldRemind(listOf(entry(Meal.BREAKFAST)), Meal.LUNCH))
        assertFalse(shouldRemind(listOf(entry(Meal.BREAKFAST), entry(Meal.LUNCH)), Meal.LUNCH))
    }

    @Test fun defaultIsOffAtLunch() {
        val default = ReminderSettings()
        assertFalse(default.enabled)
        assertEquals(lunchTime, default.time)
    }

    private fun entry(meal: Meal) = LogEntry(
        id = "e-$meal",
        userId = "u",
        date = LocalDate.of(2026, 10, 10),
        meal = meal,
        what = Logged.FoodRef("f", "Bread"),
        portion = Portion(100.0),
        nutrients = Nutrients(250.0, 8.0, 45.0, 3.0),
        isEstimate = false,
        createdAt = Instant.EPOCH,
    )
}
