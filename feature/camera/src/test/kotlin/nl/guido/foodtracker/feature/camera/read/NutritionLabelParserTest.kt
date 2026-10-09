package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelParserTest {
    /** Builds a label table: each row is a list of (text, x) cells at the same height. */
    private fun table(vararg rows: List<Pair<String, Float>>): List<TextLine> =
        rows.flatMapIndexed { i, cells ->
            cells.map { (text, x) -> TextLine(text, x, i * 40f, x + text.length * 12f, i * 40f + 30f) }
        }

    private fun row(vararg cells: Pair<String, Float>) = cells.toList()

    @Test fun `Dutch label with per 100 g and per portion columns`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Voedingswaarde" to 0f, "per 100 g" to 300f, "per portie (30 g)" to 500f),
                row("Energie" to 0f, "1046 kJ / 250 kcal" to 300f, "314 kJ / 75 kcal" to 500f),
                row("Vetten" to 0f, "9,5 g" to 300f, "2,9 g" to 500f),
                row("waarvan verzadigde vetzuren" to 0f, "3,2 g" to 300f, "1,0 g" to 500f),
                row("Koolhydraten" to 0f, "30 g" to 300f, "9,0 g" to 500f),
                row("waarvan suikers" to 0f, "12 g" to 300f, "3,6 g" to 500f),
                row("Vezels" to 0f, "4,1 g" to 300f, "1,2 g" to 500f),
                row("Eiwitten" to 0f, "8,1 g" to 300f, "2,4 g" to 500f),
                row("Zout" to 0f, "1,2 g" to 300f, "0,36 g" to 500f),
            ),
        )
        assertEquals(LabelValues(kcal = 250.0, protein = 8.1, carbs = 30.0, fat = 9.5), v)
    }

    @Test fun `each row read as one line by the camera`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Voedingswaarde per 100 g" to 0f),
                row("Energie 1600 kJ/382 kcal" to 0f),
                row("Vet 3,1 g" to 0f),
                row("- waarvan verzadigd 0,6 g" to 0f),
                row("Koolhydraten 75 g" to 0f),
                row("Eiwitten 11 g" to 0f),
            ),
        )
        assertEquals(LabelValues(kcal = 382.0, protein = 11.0, carbs = 75.0, fat = 3.1), v)
    }

    @Test fun `kcal on its own row under kJ`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Energie" to 0f, "kJ" to 200f, "1046" to 300f),
                row("kcal" to 200f, "250" to 300f),
                row("Vetten" to 0f, "9,5" to 300f),
            ),
        )
        assertEquals(250.0, v.kcal)
        assertEquals(9.5, v.fat)
    }

    @Test fun `only kJ given is turned into kcal`() {
        val v = NutritionLabelParser.parse(table(row("Energie 1046 kJ" to 0f)))
        assertEquals(250.0, v.kcal)
    }

    @Test fun `drink label per 100 ml`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Gemiddelde voedingswaarde per 100 ml" to 0f),
                row("Energie 180 kJ / 42 kcal" to 0f),
                row("Vetten 0 g" to 0f),
                row("Koolhydraten 10,6 g" to 0f),
                row("Eiwitten 0 g" to 0f),
            ),
        )
        assertTrue(v.perMl)
        assertEquals(LabelValues(42.0, 0.0, 10.6, 0.0, perMl = true), v)
    }

    @Test fun `multilingual rows and accents`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Énergie / Energie 2000 kJ / 478 kcal" to 0f),
                row("Matières grasses / Vetten / Fett 25 g" to 0f),
                row("dont acides gras saturés / waarvan verzadigde vetzuren 15 g" to 0f),
                row("Glucides / Koolhydraten 55 g" to 0f),
                row("Protéines / Eiwitten / Eiweiß 6,5 g" to 0f),
            ),
        )
        assertEquals(LabelValues(478.0, 6.5, 55.0, 25.0), v)
    }

    @Test fun `less-than values`() {
        val v = NutritionLabelParser.parse(table(row("Vetten <0,5 g" to 0f)))
        assertEquals(0.5, v.fat)
    }

    @Test fun `a photo with no table finds nothing`() {
        val v = NutritionLabelParser.parse(table(row("Ingrediënten: tarwebloem, water, gist" to 0f)))
        assertEquals(0, v.foundCount)
        assertNull(v.kcal)
    }
}
