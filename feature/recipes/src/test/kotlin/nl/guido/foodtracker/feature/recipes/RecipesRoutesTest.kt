package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `log and portion screens take an optional meal`() {
        assertEquals("recipe-log/{recipeId}?meal={meal}", RecipesRoutes.LOG)
        assertEquals("batch-portion/{batchId}?meal={meal}", RecipesRoutes.BATCH_PORTION)
    }
}
