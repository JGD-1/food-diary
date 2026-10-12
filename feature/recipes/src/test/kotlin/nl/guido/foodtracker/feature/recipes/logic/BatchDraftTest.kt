package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDraftTest {
    private val rice = Nutrients(130.0, 2.7, 28.0, 0.3)

    @Test
    fun `a draft comes back exactly as kept, half-typed grams included`() {
        val draft = BatchDraft(
            recipeId = "r1",
            name = "Chili",
            lines = listOf(DraftLine("f1", "Rice", rice, "25", null), DraftLine("f2", "Eggs", rice, "100", "2 eggs")),
            cookedText = "1,2",
            panId = "p1",
            piecesText = "4",
            cookedOn = "2026-10-12",
            scale = 2.0,
            saveToRecipe = true,
            potOnScale = true,
            emptyPotText = "850",
        )
        assertEquals(draft, BatchDraft.decode(draft.encode()))
    }

    @Test
    fun `an unreadable or missing draft is no draft`() {
        assertNull(BatchDraft.decode(null))
        assertNull(BatchDraft.decode("not json"))
    }

    @Test
    fun `only a draft with something typed is worth keeping`() {
        assertFalse(BatchDraft(cookedOn = "2026-10-12").hasContent)
        assertFalse(BatchDraft(name = "  ", cookedOn = "2026-10-12").hasContent)
        assertTrue(BatchDraft(name = "Soup", cookedOn = "2026-10-12").hasContent)
        assertTrue(BatchDraft(lines = listOf(DraftLine("f1", "Rice", rice)), cookedOn = "2026-10-12").hasContent)
    }

    @Test
    fun `draft lines turn back into the same ingredient lines`() {
        val line = EditableIngredient("f1", "Rice", rice, "250", "1 cup")
        val back = DraftLine.of(line).toEditable()
        assertEquals(line.copy(key = back.key), back)
    }

    @Test
    fun `with the pot on the scale an ingredient is the reading minus pot and what is already in`() {
        assertEquals(300.0, ingredientFromPot(reading = 1150.0, emptyPot = 850.0, alreadyIn = 0.0)!!, 0.001)
        assertEquals(120.0, ingredientFromPot(reading = 1270.0, emptyPot = 850.0, alreadyIn = 300.0)!!, 0.001)
        // The scale set to zero with the pot on it: no empty pot weight.
        assertEquals(200.0, ingredientFromPot(reading = 500.0, emptyPot = 0.0, alreadyIn = 300.0)!!, 0.001)
    }

    @Test
    fun `a pot reading that is not more than before fills in nothing`() {
        assertNull(ingredientFromPot(reading = 1150.0, emptyPot = 850.0, alreadyIn = 300.0))
        assertNull(ingredientFromPot(reading = 1000.0, emptyPot = 850.0, alreadyIn = 300.0))
    }

    @Test
    fun `the scale should show the empty pot plus every ingredient so far`() {
        val lines = listOf(
            EditableIngredient("f1", "Rice", rice, "300"),
            EditableIngredient("f2", "Beans", rice, ""),
            EditableIngredient("f3", "Onion", rice, "120"),
        )
        assertEquals(1270.0, potReadingNow(850.0, lines), 0.001)
    }
}
