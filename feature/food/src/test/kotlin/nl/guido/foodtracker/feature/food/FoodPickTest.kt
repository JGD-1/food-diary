package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.feature.food.pick.PickChip
import nl.guido.foodtracker.feature.food.pick.parseAmount
import nl.guido.foodtracker.feature.food.pick.pickChips
import nl.guido.foodtracker.feature.food.pick.pickedFood
import nl.guido.foodtracker.feature.food.pick.recentFirst
import nl.guido.foodtracker.feature.food.pick.recentIngredientIds
import nl.guido.foodtracker.feature.food.pieces.CommonPiecesParser
import nl.guido.foodtracker.feature.food.pieces.PIECES_ASSET
import nl.guido.foodtracker.feature.food.pieces.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** S13, S14, S21: the shared food picker's lists and amounts. */
class FoodPickTest {
    private val per100 = Nutrients(100.0, 1.0, 1.0, 1.0)
    private fun ing(id: String) = Ingredient(id, id, 100.0, per100)
    private fun batch(on: String, vararg ids: String) =
        Batch(on, "h", null, on, ids.map(::ing), cookedWeightG = 500.0, cookedOn = LocalDate.parse(on))

    @Test fun recentIngredientsNewestBatchFirstThenRecipes() {
        val batches = listOf(batch("2026-10-01", "rice", "onion"), batch("2026-10-09", "pasta", "onion"))
        val recipes = listOf(Recipe("r", "h", "Soup", listOf(ing("leek"), ing("rice"))))
        assertEquals(listOf("pasta", "onion", "rice", "leek"), recentIngredientIds(batches, recipes))
        assertEquals(listOf("pasta", "onion"), recentIngredientIds(batches, recipes, limit = 2))
    }

    @Test fun recentlyUsedFoundFirst() {
        val a = food("a"); val b = food("b"); val c = food("c")
        assertEquals(listOf(c, a, b), recentFirst(listOf(a, b, c), listOf(c.id)))
    }

    @Test fun eggChipsCountPieces() {
        val egg = Piece("1 egg", "1 ei", 55.0, "eggs", "eieren")
        val chips = pickChips(food("Egg"), egg, "en")
        assertEquals(listOf(55.0, 110.0, 165.0), chips.map { it.grams })
        assertEquals(listOf(1, 2, 3), chips.map { it.count })
        assertEquals("2 eggs", egg.label(2, "en"))
        assertEquals("3 eieren", egg.label(3, "nl"))
        assertEquals("1 ei", egg.label(1, "nl"))
    }

    @Test fun halfAvocadoIsNotCounted() {
        val half = Piece("½ avocado", "½ avocado", 70.0)
        assertEquals(listOf(PickChip(PickChip.Kind.PIECE, 70.0)), pickChips(food("Avocado"), half, "en"))
        assertEquals("½ avocado", half.label(2, "en"))
    }

    @Test fun servingAndPackChips() {
        val yoghurt = Food("y", "Yoghurt", per100g = per100, source = FoodOrigin.OFF, servingG = 150.0, packageG = 500.0)
        assertEquals(
            listOf(PickChip(PickChip.Kind.SERVING, 150.0), PickChip(PickChip.Kind.PACK, 500.0)),
            pickChips(yoghurt, null, "en"),
        )
        // A pot that is one serving gets one chip.
        assertEquals(1, pickChips(yoghurt.copy(packageG = 150.0), null, "en").size)
    }

    @Test fun labelStaysOnlyWithTheChipsGrams() {
        val f = food("Egg")
        val chip = PickChip(PickChip.Kind.PIECE, 110.0, 2)
        assertEquals(PickedFood(f.id, 110.0, "2 eggs"), pickedFood(f, 110.0, chip, "2 eggs"))
        assertEquals(PickedFood(f.id, 120.0, null), pickedFood(f, 120.0, chip, "2 eggs"))
        assertEquals(PickedFood(f.id, 120.0, null), pickedFood(f, 120.0, null, null))
    }

    @Test fun typedAmounts() {
        assertEquals(12.5, parseAmount("12,5"))
        assertEquals(150.0, parseAmount(" 150 "))
        assertNull(parseAmount("0"))
        assertNull(parseAmount("abc"))
    }

    @Test fun bundledPiecesCanBeCounted() {
        val pieces = File("src/main/assets/$PIECES_ASSET").bufferedReader().use { CommonPiecesParser.parse(it) }
        val egg = pieces.getValue(nevoId("84"))
        assertEquals("2 eggs", egg.label(2, "en"))
        assertEquals("2 eieren", egg.label(2, "nl"))
        assertTrue(pieces.values.count { it.countable("en") && it.countable("nl") } > 40)
    }
}
