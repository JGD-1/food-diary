package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookingTest {
    private val raw = Nutrients(2400.0, 150.0, 200.0, 90.0)

    @Test
    fun `the screen shows the step you are in`() {
        assertEquals(CookStep.IN, cookStep(dishingUp = false, dishedUp = false))
        assertEquals(CookStep.DISH_UP, cookStep(dishingUp = true, dishedUp = false))
        assertEquals(CookStep.DONE, cookStep(dishingUp = true, dishedUp = true))
    }

    @Test
    fun `dish up starts with my plate, my partner's plate and a fridge box`() {
        val rows = cookingStartRows("me", listOf(HouseholdMember("me", "Guido"), HouseholdMember("p", "Sam")))
        assertEquals(listOf(DishKind.PLATE, DishKind.PLATE, DishKind.FRIDGE), rows.map { it.kind })
        assertNull(rows[0].forUserId)
        assertEquals("p", rows[1].forUserId)
    }

    @Test
    fun `an empty fridge box is not saved`() {
        val batch = nl.guido.foodtracker.core.model.Batch(
            id = "b", householdId = "h", recipeId = null, name = "Chili",
            ingredients = listOf(Ingredient("f", "Mince", 500.0, raw * 0.2)),
            cookedWeightG = 800.0, cookedOn = java.time.LocalDate.of(2026, 11, 10),
        )
        val rows = cookingStartRows("me", emptyList()).map { if (it.kind == DishKind.PLATE) it.copy(gramsText = "400") else it }
        val outcome = dishOutcome(batch, "me", rows)
        assertEquals(0, outcome.containers.size)
        assertEquals(400.0, outcome.myGrams!!, 0.0)
    }

    @Test
    fun `done lists my plate first, then others, then boxes, each with its share`() {
        val rows = listOf(
            DishRow(kind = DishKind.FRIDGE, gramsText = "400"),
            DishRow(kind = DishKind.PLATE, forUserId = "p", gramsText = "400"),
            DishRow(kind = DishKind.PLATE, gramsText = "400"),
            DishRow(kind = DishKind.FREEZER, gramsText = ""),
        )
        val lines = doneLines("me", rows, restText = "", raw = raw)
        assertEquals(3, lines.size)
        assertNull(lines[0].forUserId)
        assertEquals(DishKind.PLATE, lines[0].kind)
        assertEquals("p", lines[1].forUserId)
        assertEquals(DishKind.FRIDGE, lines[2].kind)
        assertEquals(800, lines[0].kcal)
    }

    @Test
    fun `the rest left in the pot shows as a fridge box`() {
        val rows = listOf(DishRow(kind = DishKind.PLATE, gramsText = "300"))
        val lines = doneLines("me", rows, restText = "900", raw = raw)
        assertEquals(2, lines.size)
        assertEquals(DishKind.FRIDGE, lines[1].kind)
        assertEquals(900.0, lines[1].grams, 0.0)
        assertEquals(1800, lines[1].kcal)
    }

    @Test
    fun `my own id on a plate counts as my plate`() {
        val lines = doneLines("me", listOf(DishRow(kind = DishKind.PLATE, forUserId = "me", gramsText = "200")), "", raw)
        assertNull(lines.single().forUserId)
    }

    @Test
    fun `a cook can be saved as a recipe with a name and ingredients`() {
        val ingredients = listOf(Ingredient("f", "Mince", 500.0, raw * 0.2))
        val recipe = recipeFromCook("r", "h", "  Chili  ", ingredients, pieces = null)!!
        assertEquals("Chili", recipe.name)
        assertEquals(ingredients, recipe.ingredients)
        assertNull(recipeFromCook("r", "h", " ", ingredients, null))
        assertNull(recipeFromCook("r", "h", "Chili", emptyList(), null))
    }

    @Test
    fun `a recipe's lines go below what is already in the pot`() {
        val onion = EditableIngredient("o", "Onion", Nutrients(40.0, 1.0, 9.0, 0.0), gramsText = "150")
        val recipe = Recipe("r", "h", "Chili", listOf(Ingredient("f", "Mince", 500.0, raw * 0.2)))
        val lines = withRecipeLines(listOf(onion), recipe)
        assertEquals(listOf("Onion", "Mince"), lines.map { it.name })
        assertEquals(500.0, lines[1].grams!!, 0.0)
    }
}
