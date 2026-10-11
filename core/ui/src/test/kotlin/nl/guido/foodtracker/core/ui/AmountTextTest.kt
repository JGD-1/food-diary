package nl.guido.foodtracker.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {
    @Test
    fun `label and grams read as one amount`() {
        assertEquals("1 apple ≈ 150 g", amountText("1 apple", 150.0))
        assertEquals("1 glass ≈ 250 ml", amountText("1 glass", 250.0, isDrink = true))
    }

    @Test
    fun `no label or a label that is the amount shows grams once`() {
        assertEquals("150 g", amountText(null, 150.0))
        assertEquals("150 g", amountText("  ", 150.0))
        assertEquals("150 g", amountText("150 g", 150.0))
        assertEquals("12 g", amountText(null, 12.46))
    }

    @Test
    fun `grams are whole from 10 up and one decimal below`() {
        assertEquals("151", formatGrams(150.6))
        assertEquals("7.5", formatGrams(7.46))
        assertEquals("3", formatGrams(3.0))
    }
}
