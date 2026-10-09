package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditableIngredientTest {
    private val per100g = Nutrients(100.0, 1.0, 2.0, 3.0)

    @Test
    fun linesBecomeIngredientsOnlyWhenAllHaveGrams() {
        val a = EditableIngredient("a", "Rice", per100g, "200")
        val b = EditableIngredient("b", "Beans", per100g, "")
        assertNull(listOf(a, b).toIngredients())
        assertEquals(listOf(Ingredient("a", "Rice", 200.0, per100g)), listOf(a, b).filledIn())
        assertEquals(2, listOf(a, b.copy(gramsText = "150,5")).toIngredients()!!.size)
    }

    @Test
    fun savedIngredientRoundTripsThroughTheEditor() {
        val ingredient = Ingredient("a", "Rice", 72.5, per100g)
        assertEquals(ingredient, EditableIngredient.of(ingredient).toIngredient())
    }

    @Test
    fun gramsAreShownWithoutNeedlessDecimals() {
        assertEquals("250", formatGrams(250.0))
        assertEquals("72.5", formatGrams(72.5))
        assertEquals("33.3", formatGrams(100.0 / 3))
    }
}
