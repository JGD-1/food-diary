package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BatchCookingTest {
    private val per100 = Nutrients(kcal = 100.0, protein = 1.0, carbs = 1.0, fat = 1.0)
    private fun line(grams: String) = EditableIngredient("f", "Food", per100, grams)
    private fun ingredient(id: String, grams: Double) = Ingredient(id, "Food", grams, per100)

    @Test
    fun `times two doubles every line, half halves it`() {
        val lines = listOf(line("200"), line("75"))
        assertEquals(listOf("400", "150"), lines.rescaled(1.0, 2.0).map { it.gramsText })
        assertEquals(listOf("100", "37.5"), lines.rescaled(1.0, 0.5).map { it.gramsText })
    }

    @Test
    fun `going from times two to half keeps grams typed by hand`() {
        val lines = listOf(line("410"))
        assertEquals("102.5", lines.rescaled(2.0, 0.5).single().gramsText)
    }

    @Test
    fun `lines without grams stay empty`() {
        assertEquals("", listOf(line("")).rescaled(1.0, 2.0).single().gramsText)
    }

    @Test
    fun `amounts changed when grams or foods differ`() {
        val recipe = listOf(ingredient("a", 200.0), ingredient("b", 50.0))
        assertFalse(amountsChanged(recipe, listOf(ingredient("a", 200.0), ingredient("b", 50.0))))
        assertTrue(amountsChanged(recipe, listOf(ingredient("a", 220.0), ingredient("b", 50.0))))
        assertTrue(amountsChanged(recipe, listOf(ingredient("a", 200.0))))
        assertTrue(amountsChanged(recipe, listOf(ingredient("a", 200.0), ingredient("c", 50.0))))
    }

    @Test
    fun `cook date can be today or earlier, never later`() {
        val today = LocalDate.of(2026, 10, 11)
        assertTrue(cookDateAllowed(today, today))
        assertTrue(cookDateAllowed(today.minusDays(5), today))
        assertFalse(cookDateAllowed(today.plusDays(1), today))
    }
}
