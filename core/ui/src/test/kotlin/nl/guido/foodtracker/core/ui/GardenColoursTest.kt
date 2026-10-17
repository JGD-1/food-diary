package nl.guido.foodtracker.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The Garden colours stay readable: text and icon ink at least 4.5 : 1 on what they sit on (WCAG AA). */
class GardenColoursTest {
    private val palettes = listOf("light" to LightFoodPalette, "dark" to DarkFoodPalette)

    @Test
    fun `text is readable on the background and on cards`() {
        palettes.forEach { (mode, p) ->
            listOf(p.background, p.surface, p.invite).forEach { ground ->
                assertReadable("$mode text", p.text, ground)
                assertReadable("$mode secondary text", p.textSecondary, ground)
            }
            assertReadable("$mode button text", p.onAccent, p.accent)
            assertReadable("$mode tinted text", p.accentStrong, p.accentSoft)
            assertReadable("$mode banner text", p.accentStrong, p.banner)
        }
    }

    @Test
    fun `each pastel's ink is readable on its fill`() {
        palettes.forEach { (mode, p) ->
            assertEquals(5, p.tints.all.distinct().size)
            p.tints.all.forEach { assertReadable("$mode tint", it.ink, it.fill) }
        }
    }

    @Test
    fun `the cream background from the design is used`() {
        assertEquals(Color(0xFFF5F1E6), LightFoodPalette.background)
        assertEquals(Color(0xFF4A5A50), LightFoodPalette.textSecondary)
    }

    private fun assertReadable(what: String, front: Color, back: Color) {
        val ratio = contrast(front, back)
        assertTrue("$what: contrast $ratio is below 4.5", ratio >= 4.5)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
