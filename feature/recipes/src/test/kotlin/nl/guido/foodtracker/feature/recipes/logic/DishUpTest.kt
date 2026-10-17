package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchContainer
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.StoragePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DishUpTest {
    private val raw = Nutrients(2780.0, 200.0, 250.0, 100.0)
    private val chili = Batch(
        id = "b1", householdId = "h", recipeId = null, name = "Chili",
        ingredients = listOf(Ingredient("f1", "Mince", 1000.0, raw * 0.1)),
        cookedWeightG = 2140.0, cookedOn = LocalDate.of(2026, 10, 17),
    )

    @Test
    fun `dishing up starts with my plate and a plate for everyone else`() {
        val rows = startingRows("guido", listOf(HouseholdMember("guido", "Guido"), HouseholdMember("p", "Sam")))
        assertEquals(2, rows.size)
        assertNull(rows[0].forUserId)
        assertEquals("p", rows[1].forUserId)
        assertTrue(rows.all { it.kind == DishKind.PLATE })
    }

    @Test
    fun `the whole pot is everything dished out, the guess of the rest included`() {
        val rows = listOf(
            DishRow(kind = DishKind.PLATE, gramsText = "420"),
            DishRow(kind = DishKind.PLATE, forUserId = "p", gramsText = "420"),
            DishRow(kind = DishKind.FRIDGE, gramsText = "650"),
            DishRow(kind = DishKind.FREEZER, gramsText = ""),
        )
        assertEquals(1490.0, dishedTotal(rows), 0.0)
        assertEquals(2140.0, dishedTotal(rows, "650"), 0.0)
    }

    @Test
    fun `each plate gets its share of the raw ingredients by weight`() {
        assertEquals(545.0, shareOf(raw, 420.0, 2140.0).kcal, 1.0)
        assertEquals(20, percentOf(420.0, 2140.0))
        assertEquals(1, percentOf(2.0, 2140.0))
        assertNull(percentOf(420.0, 0.0))
        assertEquals(0.0, shareOf(raw, 420.0, 0.0).kcal, 0.0)
    }

    @Test
    fun `my plate is logged, others wait, guests are counted, boxes go to the fridge or freezer`() {
        val rows = listOf(
            DishRow(kind = DishKind.PLATE, gramsText = "300"),
            DishRow(kind = DishKind.PLATE, forUserId = "guido", gramsText = "120"),
            DishRow(kind = DishKind.PLATE, forUserId = "p", gramsText = "420"),
            DishRow(kind = DishKind.GUEST, gramsText = "200"),
            DishRow(kind = DishKind.FREEZER, gramsText = "450"),
            DishRow(kind = DishKind.PLATE, forUserId = "p", gramsText = ""),
        )
        val out = dishOutcome(chili, "guido", rows, restText = "650")
        assertEquals(420.0, out.myGrams!!, 0.0)
        val waiting = out.portions.single { it.isWaiting }
        assertEquals("p", waiting.userId)
        assertEquals("guido", waiting.servedBy)
        assertEquals(420.0, waiting.grams, 0.0)
        val guest = out.portions.single { !it.isWaiting }
        assertEquals("guido", guest.userId)
        assertNull(guest.logEntryId)
        assertEquals(listOf(StoragePlace.FREEZER, StoragePlace.FRIDGE), out.containers.map { it.place })
        assertEquals(listOf(1, 2), out.containers.map { it.number })
        assertEquals(listOf(450.0, 650.0), out.containers.map { it.grams })
    }

    @Test
    fun `without my own plate nothing is logged for me`() {
        val out = dishOutcome(chili, "guido", listOf(DishRow(kind = DishKind.FRIDGE, gramsText = "900")))
        assertNull(out.myGrams)
        assertTrue(out.portions.isEmpty())
    }

    @Test
    fun `the fridge lists boxes with something left, and what is left after portions`() {
        val boxes = listOf(
            BatchContainer("c1", "b1", "h", 650.0, StoragePlace.FRIDGE, 1),
            BatchContainer("c2", "b1", "h", 300.0, StoragePlace.FRIDGE, 2),
            BatchContainer("c3", "b1", "h", 400.0, StoragePlace.FRIDGE, 3, deleted = true),
            BatchContainer("c4", "gone", "h", 400.0, StoragePlace.FRIDGE, 1),
        )
        val portions = listOf(
            BatchPortion("p1", "b1", "h", "guido", 310.0, logEntryId = "e1", containerId = "c1"),
            BatchPortion("p2", "b1", "h", "p", 300.0, logEntryId = "e2", containerId = "c2"),
        )
        val list = storedBoxes(boxes, listOf(chili), portions)
        assertEquals(listOf("c1"), list.map { it.container.id })
        assertEquals(340.0, list.single().gramsLeft, 0.0)
    }

    @Test
    fun `a half-finished dish-up comes back exactly as kept`() {
        val draft = BatchDraft(
            name = "Chili",
            cookedOn = "2026-10-17",
            dishingUp = true,
            dishRows = listOf(DishRow(key = "k1", kind = DishKind.PLATE, forUserId = "p", gramsText = "42")),
            restText = "100",
        )
        assertEquals(draft, BatchDraft.decode(draft.encode()))
    }
}
