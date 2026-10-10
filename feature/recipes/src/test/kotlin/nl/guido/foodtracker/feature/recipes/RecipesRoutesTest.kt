package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecipesRoutesTest {
    @Test
    fun `the meal from Log food is used when given`() {
        assertEquals(Meal.BREAKFAST, RecipesRoutes.mealArg("BREAKFAST"))
        assertEquals(Meal.DRINKS, RecipesRoutes.mealArg("DRINKS"))
    }

    @Test
    fun `no or unknown meal falls back to the time of day`() {
        assertNull(RecipesRoutes.mealArg(null))
        assertNull(RecipesRoutes.mealArg("BRUNCH"))
    }

    @Test
    fun `log and portion screens take an optional meal and day`() {
        assertEquals("recipe-log/{recipeId}?meal={meal}&date={date}", RecipesRoutes.LOG)
        assertEquals("batch-portion/{batchId}?meal={meal}&date={date}", RecipesRoutes.BATCH_PORTION)
    }

    @Test
    fun `the day from Today is used, else today`() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 8), RecipesRoutes.dateArg("2026-10-08", today))
        assertEquals(today, RecipesRoutes.dateArg(null, today))
        assertEquals(today, RecipesRoutes.dateArg("yesterday", today))
    }

    @Test
    fun `the shared route builders match these patterns`() {
        val route = Routes.batchPortion("b1", Meal.LUNCH, LocalDate.of(2026, 10, 8))
        assertEquals("batch-portion/b1?meal=LUNCH&date=2026-10-08", route)
    }
}
