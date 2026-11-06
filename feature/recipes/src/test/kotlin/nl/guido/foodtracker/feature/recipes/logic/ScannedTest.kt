package nl.guido.foodtracker.feature.recipes.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannedTest {
    @Test fun `a scanned product becomes an ingredient with its grams`() {
        val picked = scannedIngredient("food-1", 250.0)!!
        assertEquals("food-1", picked.foodId)
        assertEquals(250.0, picked.grams, 0.0)
    }

    @Test fun `no grams from the camera leaves the amount empty`() {
        assertEquals(0.0, scannedIngredient("food-1", null)!!.grams, 0.0)
        assertEquals(0.0, scannedIngredient("food-1", -3.0)!!.grams, 0.0)
    }

    @Test fun `only weighing is not a scanned product`() {
        assertNull(scannedIngredient(null, 300.0))
        assertNull(scannedIngredient("", 300.0))
    }
}
