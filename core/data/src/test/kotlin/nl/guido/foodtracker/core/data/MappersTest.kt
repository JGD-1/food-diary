package nl.guido.foodtracker.core.data

import nl.guido.foodtracker.core.data.db.toEntity
import nl.guido.foodtracker.core.data.db.toModel
import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Favourite
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

    @Test
    fun favouriteRoundTrip() {
        val favourite = Favourite(id = "f1", userId = "u1", recipeId = "r1", usualPortion = Portion(350.0, "1 bowl"))
        assertEquals(favourite, favourite.toEntity(now = 5).toModel())
        val noPortion = favourite.copy(usualPortion = null)
        assertEquals(noPortion, noPortion.toEntity(now = 5).toModel())
    }

    @Test
    fun version3FieldsRoundTrip() {
        val food = Food(
            id = "f1", name = "Muesli", brand = "Brand", barcode = "123",
            per100g = Nutrients(370.0, 10.0, 60.0, 6.0, fibre = 8.0, sugar = 12.0, salt = 0.1),
            source = FoodOrigin.OFF, servingG = 45.0, packageG = 750.0,
        )
        assertEquals(food, food.toEntity(now = 5).toModel())

        val entry = LogEntry(
            id = "e1", userId = "u1", date = LocalDate.of(2026, 10, 10), meal = Meal.BREAKFAST,
            what = Logged.FoodRef("f1", "Muesli"), portion = Portion(45.0),
            nutrients = Nutrients(166.5, 4.5, 27.0, 2.7, fibre = 3.6, sugar = null, salt = 0.05),
            isEstimate = false, createdAt = Instant.ofEpochMilli(1_000),
        )
        assertEquals(entry, entry.toEntity(now = 5).toModel())

        val pinnedFood = Favourite(id = "fav", userId = "u1", foodId = "f1", usualPortion = Portion(45.0))
        assertEquals(pinnedFood, pinnedFood.toEntity(now = 5).toModel())

        val batch = Batch(
            "b1", "h1", null, "Chili", emptyList(), cookedWeightG = 1200.0,
            cookedOn = LocalDate.of(2026, 10, 9), finishedOn = LocalDate.of(2026, 10, 12),
        )
        assertEquals(batch, batch.toEntity(now = 5).toModel())

        val portion = BatchPortion("p1", "b1", "h1", "u1", 350.0, logEntryId = "e1")
        assertEquals(portion, portion.toEntity(now = 5).toModel())

        val profile = UserProfile(
            id = "u1", name = "Guido", birthYear = 1990, sex = Sex.MALE, heightCm = 180,
            activity = ActivityLevel.LIGHT, startWeightKg = 85.0, targetWeightKg = 80.0, weeklyPaceKg = 0.5,
            proteinGoalG = 120,
        )
        assertEquals(profile, profile.toEntity(now = 5).toModel())
    }
}
