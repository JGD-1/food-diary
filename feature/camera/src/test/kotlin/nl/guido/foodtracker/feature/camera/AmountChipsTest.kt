package nl.guido.foodtracker.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountChipsTest {
    private fun text(chip: AmountChip, drink: Boolean = false) = chipText(chip, "1 serving", "Whole pack", drink)

    @Test
    fun `chips use the one amount format`() {
        assertEquals("1 serving ≈ 45 g", text(AmountChip(AmountChip.Kind.SERVING, 45.0)))
        assertEquals("Whole pack ≈ 750 g", text(AmountChip(AmountChip.Kind.PACK, 750.0)))
        assertEquals("1 apple ≈ 150 g", text(AmountChip(AmountChip.Kind.PIECE, 150.0, "1 apple")))
    }

    @Test
    fun `drinks use ml`() {
        assertEquals("1 serving ≈ 250 ml", text(AmountChip(AmountChip.Kind.SERVING, 250.0), drink = true))
    }

    @Test
    fun `a piece without a label is just the amount`() {
        assertEquals("150 g", text(AmountChip(AmountChip.Kind.PIECE, 150.0)))
    }
}
