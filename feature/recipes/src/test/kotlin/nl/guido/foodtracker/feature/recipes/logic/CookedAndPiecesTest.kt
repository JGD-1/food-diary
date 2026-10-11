package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CookedAndPiecesTest {
    private val per100 = Nutrients(100.0, 1.0, 1.0, 1.0)
    private val flour = Ingredient("flour", "Flour", 500.0, per100)
    private val milk = Ingredient("milk", "Milk", 300.0, per100) // 800 g raw, 800 kcal
    private val cake = Recipe("r1", "home", "Cake", listOf(flour, milk))

    @Test
    fun `without a cooked weight portions are raw grams`() {
        assertEquals(800.0, Dish(cake).totalGrams, 0.001)
        assertEquals(800.0, cake.dishGrams, 0.001)
    }

    @Test
    fun `with a cooked weight portions are grams of the finished dish`() {
        val baked = cake.copy(cookedWeightG = 640.0)
        val dish = Dish(baked)
        assertEquals(640.0, dish.totalGrams, 0.001)
        // Half the cake is 320 g baked and still half the kcal.
        assertEquals(400.0, dish.nutrientsFor(320.0).kcal, 0.001)
        assertEquals(640.0, dish.suggestedGrams(null), 0.001)
    }

    @Test
    fun `extras go on top of the cooked weight`() {
        val dish = Dish(cake.copy(cookedWeightG = 640.0), extras = listOf(Ingredient("cream", "Cream", 60.0, per100)))
        assertEquals(700.0, dish.totalGrams, 0.001)
        assertEquals(860.0, dish.nutrientsFor(700.0).kcal, 0.001)
    }

    @Test
    fun `pieces of a recipe split the finished dish`() {
        val muffins = cake.copy(cookedWeightG = 600.0, pieces = 12)
        val dish = Dish(muffins)
        assertEquals(25.0, dish.piecesGrams(0.5)!!, 0.001)
        assertEquals(100.0, dish.piecesGrams(2.0)!!, 0.001)
        assertNull(Dish(cake).piecesGrams(1.0))
    }

    @Test
    fun `pieces of a batch split the cooked pot`() {
        val batch = Batch("b1", "home", null, "Lasagne", listOf(flour), 1200.0, LocalDate.of(2026, 10, 11), pieces = 6)
        assertEquals(200.0, batch.piecesGrams(1.0)!!, 0.001)
        assertNull(batch.copy(pieces = null).piecesGrams(1.0))
    }

    @Test
    fun `the pieces label goes on the diary line`() {
        val dish = Dish(cake.copy(pieces = 8))
        val entry = dish.logEntry("me", LocalDate.of(2026, 10, 11), Meal.SNACKS, 100.0, Instant.EPOCH, "1 piece")
        assertEquals(Portion(100.0, "1 piece"), entry.portion)
    }

    @Test
    fun `pieces must be a whole number from 1 to 500`() {
        assertEquals(12, parsePieces(" 12 "))
        assertNull(parsePieces("0"))
        assertNull(parsePieces("2.5"))
        assertNull(parsePieces(""))
    }

    @Test
    fun `amount choices are pieces, a serving and the pack`() {
        val yoghurt = Food("y", "Yoghurt", per100g = per100, source = FoodOrigin.OFF, servingG = 150.0, packageG = 500.0)
        val choices = portionChoices(yoghurt, listOf(Portion(125.0, "1 pot")), "1 serving", "Whole pack")
        assertEquals(listOf(Portion(125.0, "1 pot"), Portion(150.0, "1 serving"), Portion(500.0, "Whole pack")), choices)
    }

    @Test
    fun `a chosen amount keeps its label until grams are typed`() {
        val line = EditableIngredient("egg", "Egg", per100).withChoice(Portion(50.0, "1 egg"))
        assertEquals("50", line.gramsText)
        assertEquals("1 egg", line.toIngredient()!!.amountLabel)
        assertNull(line.withGramsText("60").amountLabel)
        assertEquals("1 egg", line.withGramsText("50").amountLabel)
    }

    @Test
    fun `a saved label comes back into the editor`() {
        val saved = Ingredient("egg", "Egg", 100.0, per100, amountLabel = "2 eggs")
        assertEquals(saved, EditableIngredient.of(saved).toIngredient())
    }
}
