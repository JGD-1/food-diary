package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DishTest {
    private val pasta = Ingredient("pasta", "Pasta", 400.0, Nutrients(350.0, 12.0, 70.0, 2.0))
    private val pesto = Ingredient("pesto", "Pesto", 100.0, Nutrients(450.0, 5.0, 5.0, 45.0))
    private val cheese = Ingredient("cheese", "Cheese", 50.0, Nutrients(400.0, 25.0, 0.0, 33.0))
    private val recipe = Recipe("r1", "home", "Pasta pesto", listOf(pasta, pesto)) // 500 g, 1850 kcal
    private val withCheese = RecipeVariant("v1", "r1", "extra cheese", listOf(cheese)) // +50 g, +200 kcal
    private val day = LocalDate.of(2026, 10, 9)
    private val now = Instant.ofEpochMilli(1_000)

    @Test
    fun plainRecipeTotals() {
        val dish = Dish(recipe)
        assertEquals(500.0, dish.totalGrams, 0.001)
        assertEquals(1850.0, dish.total.kcal, 0.001)
        assertEquals(370.0, dish.nutrientsFor(100.0).kcal, 0.001)
        assertEquals("Pasta pesto", dish.name)
    }

    @Test
    fun variantAndExtrasAddToTheDishButNotToTheRecipe() {
        val dish = Dish(recipe, withCheese, extras = listOf(cheese))
        assertEquals(600.0, dish.totalGrams, 0.001)
        assertEquals(2250.0, dish.total.kcal, 0.001)
        assertEquals(listOf(pasta, pesto), recipe.ingredients)
        assertEquals("Pasta pesto (extra cheese)", dish.name)
    }

    @Test
    fun wholeDishWithExtrasCountsTheExtrasFully() {
        val dish = Dish(recipe, extras = listOf(cheese))
        assertEquals(1850.0 + 200.0, dish.nutrientsFor(dish.totalGrams).kcal, 0.001)
    }

    @Test
    fun suggestedAmountIsWholeDishWithoutUsualPortion() {
        assertEquals(550.0, Dish(recipe, withCheese).suggestedGrams(null), 0.001)
    }

    @Test
    fun usualPortionKeepsTheSameShareWhenExtrasAreAdded() {
        // Usually half the recipe (250 of 500 g); with 50 g cheese the dish is 550 g, so half is 275 g.
        val usual = Portion(250.0)
        assertEquals(250.0, Dish(recipe).suggestedGrams(usual), 0.001)
        assertEquals(275.0, Dish(recipe, extras = listOf(cheese)).suggestedGrams(usual), 0.001)
    }

    @Test
    fun usualPortionIsRememberedAsAShareOfThePlainRecipe() {
        val dish = Dish(recipe, extras = listOf(cheese)) // 550 g
        assertEquals(250.0, dish.toUsualPortion(275.0)!!.grams, 0.001)
        assertNull(Dish(recipe.copy(ingredients = emptyList())).toUsualPortion(100.0))
    }

    @Test
    fun emptyRecipeGivesZeroInsteadOfDividingByZero() {
        val empty = Dish(recipe.copy(ingredients = emptyList()))
        assertEquals(Nutrients.ZERO, empty.nutrientsFor(100.0))
    }

    @Test
    fun newVariantCombinesTheChosenVariantWithTodaysExtras() {
        val onions = Ingredient("onion", "Fried onions", 20.0, Nutrients(600.0, 6.0, 40.0, 45.0))
        val variant = Dish(recipe, withCheese, listOf(onions)).asNewVariant("cheese and onions", id = "v2")
        assertEquals(RecipeVariant("v2", "r1", "cheese and onions", listOf(cheese, onions)), variant)
    }

    @Test
    fun recipeLogEntryKeepsExtrasSeparateFromTheRecipe() {
        val dish = Dish(recipe, withCheese, listOf(cheese))
        val entry = dish.logEntry("me", day, Meal.DINNER, 300.0, now, id = "e1")
        assertEquals(Logged.RecipeRef("r1", "Pasta pesto (extra cheese)", "v1", listOf(cheese)), entry.what)
        assertEquals(Portion(300.0), entry.portion)
        assertEquals(2250.0 / 600.0 * 300.0, entry.nutrients.kcal, 0.001)
        assertFalse(entry.isEstimate)
        assertEquals("me", entry.userId)
        assertEquals(day, entry.date)
    }

    @Test
    fun batchShareIsMyPortionOfTheCookedWeight() {
        // 500 g raw (1850 kcal) cooks down to 1000 g with water; 250 g is a quarter.
        val batch = Batch("b1", "home", "r1", "Pasta pesto", listOf(pasta, pesto), 1000.0, day)
        val mine = batch.logEntry("me", day, Meal.DINNER, 250.0, now, id = "e1")
        val partner = batch.logEntry("partner", day, Meal.DINNER, 400.0, now, id = "e2")
        assertEquals(462.5, mine.nutrients.kcal, 0.001)
        assertEquals(740.0, partner.nutrients.kcal, 0.001)
        assertEquals(Logged.BatchShare("b1", "Pasta pesto"), mine.what)
        assertEquals("partner", partner.userId)
    }

    @Test
    fun mealFollowsTheTimeOfDay() {
        assertEquals(Meal.BREAKFAST, mealAt(LocalTime.of(7, 30)))
        assertEquals(Meal.LUNCH, mealAt(LocalTime.of(12, 45)))
        assertEquals(Meal.SNACKS, mealAt(LocalTime.of(15, 30)))
        assertEquals(Meal.DINNER, mealAt(LocalTime.of(18, 30)))
        assertEquals(Meal.SNACKS, mealAt(LocalTime.of(22, 0)))
    }

    @Test
    fun gramsAcceptDutchCommaAndRejectNonsense() {
        assertEquals(250.5, parseGrams("250,5")!!, 0.001)
        assertEquals(250.0, parseGrams(" 250 ")!!, 0.001)
        assertNull(parseGrams(""))
        assertNull(parseGrams("0"))
        assertNull(parseGrams("-5"))
        assertNull(parseGrams("abc"))
    }

    @Test
    fun batchesStayUntilFinishedNewestFirst() {
        fun batch(id: String, daysAgo: Long, finished: Boolean = false) =
            Batch(id, "home", null, id, emptyList(), 1000.0, day.minusDays(daysAgo), finishedOn = if (finished) day else null)
        val list = openBatches(listOf(batch("old", 30), batch("a", 3), batch("done", 1, finished = true), batch("b", 0)), emptyList())
        assertEquals(listOf("b", "a", "old"), list.map { it.batch.id })
    }

    @Test
    fun gramsLeftCountsEveryonesPortions() {
        val pot = Batch("pot", "home", null, "Chili", emptyList(), 1000.0, day)
        val other = Batch("other", "home", null, "Soup", emptyList(), 500.0, day)
        val portions = listOf(
            BatchPortion("p1", "pot", "home", "guido", 350.0),
            BatchPortion("p2", "pot", "home", "partner", 400.0),
            BatchPortion("p3", "other", "home", "guido", 600.0),
        )
        val left = openBatches(listOf(pot, other), portions).associate { it.batch.id to it.gramsLeft }
        assertEquals(250.0, left["pot"]!!, 0.001)
        assertEquals(0.0, left["other"]!!, 0.001)
    }

    @Test
    fun myPortionIsSharedAsGramsOnlyLinkedToMyDiaryLine() {
        val pot = Batch("pot", "home", null, "Chili", emptyList(), 1000.0, day)
        val entry = pot.logEntry("guido", day, Meal.DINNER, 320.0, Instant.EPOCH, id = "line1")
        val portion = pot.portionFor(entry, "home", id = "p1")
        assertEquals(BatchPortion("p1", "pot", "home", "guido", 320.0, logEntryId = "line1"), portion)
    }
}
