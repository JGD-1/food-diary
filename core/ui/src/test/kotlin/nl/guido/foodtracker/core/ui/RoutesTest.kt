package nl.guido.foodtracker.core.ui

import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.PickedFood
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RoutesTest {
    private val day = LocalDate.of(2026, 10, 8)

    @Test
    fun `routes without extras stay plain`() {
        assertEquals("log-food", Routes.logFood())
        assertEquals("eat-out", Routes.eatOut())
        assertEquals("recipe-log/r1", Routes.recipeLog("r1"))
        assertEquals("weigh-in", Routes.weighIn())
    }

    @Test
    fun `extras are added as query arguments`() {
        assertEquals("log-food?meal=LUNCH&date=2026-10-08", Routes.logFood(Meal.LUNCH, day))
        assertEquals("batch-portion/b1?date=2026-10-08", Routes.batchPortion("b1", date = day))
        assertEquals("recipe-log/r1?meal=DINNER", Routes.recipeLog("r1", Meal.DINNER))
        assertEquals("weigh-in?id=w1", Routes.weighIn("w1"))
        assertEquals("day/2026-10-08", Routes.day(day))
        assertEquals("food-edit/f1", Routes.foodEdit("f1"))
        assertEquals("food-pick?purpose=RECIPE_INGREDIENT", Routes.foodPick(FoodPickPurpose.RECIPE_INGREDIENT))
        assertEquals("batch-edit/b1", Routes.batchEdit("b1"))
    }

    @Test
    fun `typed dish words are encoded`() {
        assertEquals("eat-out?date=2026-10-08&dish=Pizza%20%26%20fries%2B", Routes.eatOut(day, "Pizza & fries+"))
        assertEquals("eat-out", Routes.eatOut(dish = "  "))
    }

    @Test
    fun `extras join an existing query`() {
        assertEquals("camera?mode=scale&date=2026-10-08", Routes.withArgs(Routes.CAMERA_SCALE, Routes.ARG_DATE to day.toString()))
    }

    @Test
    fun `picked food survives the trip back`() {
        val picked = PickedFood("f1", 120.0, "2 eggs")
        assertEquals(picked, FoodPickResult.decode(FoodPickResult.encode(picked)))
        assertEquals(null, FoodPickResult.decode("not json"))
    }
}
