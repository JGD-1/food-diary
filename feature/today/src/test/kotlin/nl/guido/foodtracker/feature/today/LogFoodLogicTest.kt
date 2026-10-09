package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogFoodLogicTest {

    private val cola = Food("cola", "Cola", per100g = Nutrients(42.0, 0.0, 10.6, 0.0), source = FoodOrigin.OFF, isDrink = true)
    private val oats = Food("oats", "Oats", per100g = Nutrients(370.0, 13.0, 60.0, 7.0), source = FoodOrigin.NEVO)

    @Test
    fun `recent items are listed once, drinks first when logging drinks`() {
        val recent = listOf( // newest first
            line("Oats", 185.0, grams = 50.0),
            line("Cola", 139.0, Meal.DRINKS, grams = 330.0),
            line("Oats", 370.0, grams = 100.0),
        )
        assertEquals(listOf("Oats", "Cola"), recentItems(recent, Meal.BREAKFAST).map { it.displayName })
        assertEquals(listOf("Cola", "Oats"), recentItems(recent, Meal.DRINKS).map { it.displayName })
        assertEquals(50.0, recentItems(recent, Meal.LUNCH).first().portion.grams, 0.0)
    }

    @Test
    fun `suggested amount is last time's, else a usual default`() {
        val recent = listOf(line("Oats", 185.0, grams = 50.0, foodId = "oats"))
        assertEquals(50.0, defaultGramsFor("oats", false, recent), 0.0)
        assertEquals(DEFAULT_FOOD_GRAMS, defaultGramsFor("rice", false, recent), 0.0)
        assertEquals(DEFAULT_DRINK_ML, defaultGramsFor("cola", true, recent), 0.0)
    }

    @Test
    fun `a chosen food becomes a diary line with totals for the amount`() {
        val pick = pickFromFood(oats, emptyList())
        val line = entryFromPick(pick, 50.0, "me", TODAY, Meal.BREAKFAST, Instant.EPOCH, id = "x")
        assertEquals(185.0, line.nutrients.kcal, 0.001)
        assertEquals(Logged.FoodRef("oats", "Oats"), line.what)
        assertEquals(50.0, line.portion.grams, 0.0)
        assertEquals(false, line.isEstimate)
    }

    @Test
    fun `a recent line can be logged with another amount`() {
        val pick = pickFromEntry(line("Yoghurt", 194.0, grams = 200.0))!!
        assertEquals(97.0, pick.per100g.kcal, 0.001)
        assertEquals(200.0, pick.defaultGrams, 0.0)
        assertNull(pickFromEntry(restaurant("Pizza", 800)))
    }

    @Test
    fun `one-tap repeat copies the line to today`() {
        val old = restaurant("Pizza", 800, date = TODAY.minusDays(4))
        val again = repeatEntry(old, "me", TODAY, Meal.DINNER, Instant.EPOCH, id = "new")
        assertEquals(TODAY, again.date)
        assertEquals(Meal.DINNER, again.meal)
        assertTrue(again.isEstimate)
        assertEquals(old.nutrients, again.nutrients)
    }

    @Test
    fun `drinks always land in Drinks`() {
        assertEquals(Meal.DRINKS, mealFor(isDrink = true, chosen = Meal.LUNCH))
        assertEquals(Meal.LUNCH, mealFor(isDrink = false, chosen = Meal.LUNCH))
        assertEquals(Meal.DRINKS, mealFor(isDrink = cola.isDrink, chosen = Meal.DRINKS))
    }
}
