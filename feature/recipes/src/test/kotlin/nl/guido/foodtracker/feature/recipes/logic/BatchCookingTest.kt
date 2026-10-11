package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Pan
import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `the empty pan comes off the scale reading`() {
        val pot = Pan("p1", "home", "Big pot", 1250.0)
        assertEquals(830.0, cookedFromReading(2080.0, pot)!!, 0.001)
        assertEquals(2080.0, cookedFromReading(2080.0, null)!!, 0.001)
        assertNull(cookedFromReading(1200.0, pot))
        assertNull(cookedFromReading(null, pot))
    }

    @Test
    fun `pieces follow half and double, never below one`() {
        assertEquals(6, scaledPieces(12, 1.0, 0.5))
        assertEquals(24, scaledPieces(12, 1.0, 2.0))
        assertEquals(12, scaledPieces(24, 2.0, 1.0))
        assertEquals(1, scaledPieces(1, 1.0, 0.5))
    }

    @Test
    fun `pieces left come from grams left`() {
        val batch = Batch("b1", "home", null, "Lasagne", emptyList(), 1200.0, LocalDate.of(2026, 10, 11), pieces = 6)
        assertEquals(2.5, batch.piecesLeft(500.0)!!, 0.001)
        assertNull(batch.copy(pieces = null).piecesLeft(500.0))
    }

    @Test
    fun `frozen batches get their own list`() {
        val day = LocalDate.of(2026, 10, 11)
        val soup = OpenBatch(Batch("a", "home", null, "Soup", emptyList(), 1000.0, day), 1000.0)
        val chili = OpenBatch(Batch("b", "home", null, "Chili", emptyList(), 1000.0, day, frozen = true), 800.0)
        val lists = listOf(soup, chili).byFreezer()
        assertEquals(listOf(soup), lists.inPot)
        assertEquals(listOf(chili), lists.frozen)
    }

    @Test
    fun `scaling drops a label that no longer fits`() {
        val eggs = line("100").copy(amountLabel = "2 eggs")
        assertNull(listOf(eggs).rescaled(1.0, 2.0).single().amountLabel)
        assertEquals("2 eggs", listOf(eggs).rescaled(1.0, 1.0).single().amountLabel)
    }
}
