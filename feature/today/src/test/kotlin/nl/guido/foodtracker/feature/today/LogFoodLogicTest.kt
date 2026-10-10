package nl.guido.foodtracker.feature.today

import kotlinx.coroutines.runBlocking
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `favourites show their recipe with my usual portion, A to Z, skipping removed recipes`() {
        val soup = Recipe("soup", "home", "Soup", emptyList())
        val chili = Recipe("chili", "home", "chili", emptyList(), usualPortion = Portion(500.0))
        val favourites = listOf(
            Favourite("f1", "me", "soup", Portion(350.0, "1 bowl")),
            Favourite("f2", "me", "chili"),
            Favourite("f3", "me", "gone"),
        )
        val pinned = pinnedRecipes(favourites, listOf(soup, chili))
        assertEquals(listOf("chili", "soup"), pinned.map { it.id })
        assertNull(pinned[0].usualPortion)
        assertEquals(Portion(350.0, "1 bowl"), pinned[1].usualPortion)
        assertTrue(pinned.all { it.pinned })
    }

    @Test
    fun `a scanned and weighed food is logged with the camera's amount`() {
        val line = cameraEntry(cola, 330.0, "me", TODAY, Meal.DRINKS, Instant.EPOCH, id = "c")
        assertEquals(138.6, line.nutrients.kcal, 0.001)
        assertEquals(330.0, line.portion.grams, 0.0)
        assertEquals(Meal.DRINKS, line.meal)
        assertEquals(Logged.FoodRef("cola", "Cola"), line.what)
    }

    @Test
    fun `recipes and batches open for the meal chosen in Log food`() {
        assertEquals("recipe-log/r1?meal=BREAKFAST", Routes.recipeLog("r1", Meal.BREAKFAST, routeDate(TODAY, TODAY)))
        assertEquals(
            "batch-portion/b1?meal=DINNER&date=2026-10-11",
            Routes.batchPortion("b1", Meal.DINNER, routeDate(TODAY.minusDays(1), TODAY)),
        )
    }

    @Test
    fun `serving and pack sizes become amount chips, once each`() {
        val bar = oats.copy(source = FoodOrigin.OFF, servingG = 40.0, packageG = 400.0)
        assertEquals(
            listOf(AmountChip(AmountChip.Kind.SERVING, 40.0), AmountChip(AmountChip.Kind.PACK, 400.0)),
            amountChips(bar),
        )
        val pot = oats.copy(servingG = 150.0, packageG = 150.0)
        assertEquals(listOf(AmountChip(AmountChip.Kind.SERVING, 150.0)), amountChips(pot))
        assertEquals(
            listOf(AmountChip(AmountChip.Kind.PIECE, 150.0, "1 apple")),
            amountChips(oats, listOf(Portion(150.0, "1 apple"), Portion(0.0, "nothing"), Portion(80.0))),
        )
        assertTrue(amountChips(null).isEmpty())
        assertEquals(2, pickFromFood(bar, emptyList()).chips.size)
    }

    @Test
    fun `NEVO foods can't be edited, own and Open Food Facts foods can`() {
        assertFalse(canEdit(oats))
        assertTrue(canEdit(cola))
        assertTrue(canEdit(oats.copy(source = FoodOrigin.LABEL)))
        assertFalse(canEdit(null))
    }

    @Test
    fun `pinned foods come with my usual amount, A to Z, skipping missing foods`() = runBlocking {
        val favourites = listOf(
            foodFavourite("me", "oats", 60.0, id = "f1"),
            foodFavourite("me", "cola", 330.0, id = "f2"),
            foodFavourite("me", "gone", 10.0, id = "f3"),
            Favourite("f4", "me", recipeId = "soup"),
        )
        val known = listOf(oats, cola).associateBy { it.id }
        val pins = pinnedFoods(favourites) { known[it] }
        assertEquals(listOf("Cola", "Oats"), pins.map { it.food.name })
        assertEquals(60.0, pins[1].usualGrams!!, 0.0)
        assertTrue(pinnedRecipes(favourites, emptyList()).isEmpty())
    }

    @Test
    fun `the pot lists unfinished batches with what is left for everyone`() {
        val chili = Batch("b1", "home", null, "Chili", emptyList(), 2000.0, TODAY.minusDays(9))
        val soup = Batch("b2", "home", null, "Soup", emptyList(), 1500.0, TODAY.minusDays(1), finishedOn = TODAY)
        val portions = listOf(
            BatchPortion("p1", "b1", "home", "me", 400.0),
            BatchPortion("p2", "b1", "home", "partner", 350.0),
            BatchPortion("p3", "b2", "home", "me", 300.0),
        )
        val pot = potItems(listOf(soup, chili), portions)
        assertEquals(listOf("Chili"), pot.map { it.batch.name })
        assertEquals(1250.0, pot[0].gramsLeft, 0.0)
    }
}
