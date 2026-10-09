package nl.guido.foodtracker.core.data

import nl.guido.foodtracker.core.data.db.toEntity
import nl.guido.foodtracker.core.data.db.toModel
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MappersTest {
    @Test
    fun logEntryRoundTrip() {
        val entry = LogEntry(
            id = "e1", userId = "u1", date = LocalDate.of(2026, 10, 9), meal = Meal.LUNCH,
            what = Logged.Restaurant("Club sandwich", Estimate(450, 560, 680)),
            portion = Portion(1.0, "1 plate"), nutrients = Nutrients(560.0, 25.0, 50.0, 28.0),
            isEstimate = true, createdAt = Instant.ofEpochMilli(1_000),
        )
        assertEquals(entry, entry.toEntity(now = 5).toModel())
    }

    @Test
    fun recipeRoundTrip() {
        val recipe = Recipe(
            id = "r1", householdId = "h1", name = "Chili",
            ingredients = listOf(Ingredient("f1", "Beans", 400.0, Nutrients(100.0, 7.0, 13.0, 0.5))),
            pinned = true, usualPortion = Portion(350.0, "1 bowl"),
        )
        assertEquals(recipe, recipe.toEntity(now = 5).toModel())
    }
}
