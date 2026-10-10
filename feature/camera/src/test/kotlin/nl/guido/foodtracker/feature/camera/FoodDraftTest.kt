package nl.guido.foodtracker.feature.camera

import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.camera.read.LabelValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDraftTest {
    @Test fun `label values fill the form`() {
        val d = FoodDraft.fromLabel(LabelValues(250.0, 8.1, 30.0, null), barcode = "5449000000996", drinkHint = false)
        assertEquals("250", d.kcal)
        assertEquals("8.1", d.protein)
        assertEquals("", d.fat)
        assertEquals("5449000000996", d.barcode)
        assertFalse(d.isDrink)
    }

    @Test fun `per 100 ml or opened from Drinks means a drink`() {
        assertTrue(FoodDraft.fromLabel(LabelValues(perMl = true), null, drinkHint = false).isDrink)
        assertTrue(FoodDraft.fromLabel(LabelValues(), null, drinkHint = true).isDrink)
    }

    @Test fun `saved as a label food owned by the user`() {
        val food = FoodDraft("Crackers ", "", "382", "11", "75", "3,1", barcode = "123").toFood("id-1", "user-1")!!
        assertEquals("Crackers", food.name)
        assertNull(food.brand)
        assertEquals(Nutrients(382.0, 11.0, 75.0, 3.1), food.per100g)
        assertEquals(FoodOrigin.LABEL, food.source)
        assertEquals("user-1", food.ownerId)
        assertEquals("123", food.barcode)
    }

    @Test fun `empty macros count as zero`() {
        val food = FoodDraft("Cola", kcal = "42", isDrink = true).toFood("id", "u")!!
        assertEquals(Nutrients(42.0, 0.0, 0.0, 0.0), food.per100g)
        assertTrue(food.isDrink)
    }

    @Test fun `name and kcal are needed`() {
        assertNull(FoodDraft(name = "", kcal = "100").toFood("id", "u"))
        assertNull(FoodDraft(name = "Bread", kcal = "").toFood("id", "u"))
        assertNull(FoodDraft(name = "Bread", kcal = "100", fat = "lots").toFood("id", "u"))
    }

    @Test fun `fibre sugar and salt are optional and empty means unknown`() {
        val d = FoodDraft.fromLabel(LabelValues(250.0, 8.1, 30.0, 9.5, fibre = 4.1, salt = 1.2), null, drinkHint = false)
        assertEquals("4.1", d.fibre)
        assertEquals("", d.sugar)
        val food = d.copy(name = "Crackers").toFood("id", "u")!!
        assertEquals(4.1, food.per100g.fibre!!, 1e-9)
        assertNull(food.per100g.sugar)
        assertEquals(1.2, food.per100g.salt!!, 1e-9)
        assertNull(d.copy(name = "Crackers", salt = "a pinch").toFood("id", "u"))
    }

    @Test fun `typed amounts`() {
        assertEquals(12.5, parseAmount("12,5")!!, 0.0)
        assertEquals(150.0, parseAmount(" 150 ")!!, 0.0)
        assertNull(parseAmount("-3"))
        assertNull(parseAmount("abc"))
    }
}
