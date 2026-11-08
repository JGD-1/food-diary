package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.feature.recipes.logic.DishKind
import nl.guido.foodtracker.feature.recipes.logic.opensOnDishUp
import nl.guido.foodtracker.feature.recipes.logic.startingRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Round 12 (G1): "Opscheppen voor iedereen" on a saved recipe. */
class DishUpEveryoneTest {
    @Test
    fun `the button opens Cook a batch on Dish up`() {
        assertEquals("recipes/batch?recipeId=r1&dishUp=true", RecipesRoutes.newBatch("r1", dishUp = true))
        assertEquals(
            "recipes/batch?recipeId=r1&date=2026-11-07&dishUp=true",
            RecipesRoutes.newBatch("r1", LocalDate.of(2026, 11, 7), dishUp = true),
        )
        // Cook a batch keeps working as before.
        assertEquals("recipes/batch?recipeId=r1", RecipesRoutes.newBatch("r1"))
        assertEquals("recipes/batch", RecipesRoutes.newBatch())
        // Without a recipe there is nothing to dish up.
        assertEquals("recipes/batch", RecipesRoutes.newBatch(null, dishUp = true))
    }

    @Test
    fun `only from a saved recipe, and only when asked`() {
        assertTrue(opensOnDishUp(true, fromRecipe = true))
        assertFalse(opensOnDishUp(true, fromRecipe = false))
        assertFalse(opensOnDishUp(false, fromRecipe = true))
        assertFalse(opensOnDishUp(null, fromRecipe = true))
    }

    @Test
    fun `my plate plus one for everyone in the household`() {
        val rows = startingRows("me", listOf(HouseholdMember("me", "Guido"), HouseholdMember("p", "Partner")))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == DishKind.PLATE })
        assertEquals(listOf(null, "p"), rows.map { it.forUserId })
    }
}
