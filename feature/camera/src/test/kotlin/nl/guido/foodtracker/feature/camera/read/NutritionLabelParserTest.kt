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
        assertEquals(
            LabelValues(kcal = 250.0, protein = 8.1, carbs = 30.0, fat = 9.5, fibre = 4.1, sugar = 12.0, salt = 1.2),
            v,
        )
        assertEquals(4, v.foundCount)
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

    @Test fun `fibre sugar and salt in other languages, sodium and free sugars ignored`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Nutrition per 100 g" to 0f),
                row("Energy 1600 kJ / 382 kcal" to 0f),
                row("Carbohydrate 60 g" to 0f),
                row("of which sugars / davon Zucker 22 g" to 0f),
                row("Ballaststoffe / Fibre 6,5 g" to 0f),
                row("Sodium 0,4 g" to 0f),
                row("Sel / Salz / Salt 1,0 g" to 0f),
            ),
        )
        assertEquals(22.0, v.sugar)
        assertEquals(6.5, v.fibre)
        assertEquals(1.0, v.salt)
        assertEquals(60.0, v.carbs)
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

    /** Lines in reading order, one under the other (or side by side when [sideways], as in a photo taken turned). */
    private fun text(vararg lines: String, sideways: Boolean = false): List<TextLine> = lines.mapIndexed { i, t ->
        if (sideways) TextLine(t, 1000f - i * 40f, 0f, 1030f - i * 40f, t.length * 12f)
        else TextLine(t, 0f, i * 40f, t.length * 12f, i * 40f + 30f)
    }

    // Jumbo runderriblap: no table, the values are a sentence.
    private val riblap = arrayOf(
        "Runderriblap",
        "Ingrediënten: rundvlees.",
        "Bereidingswijze: braadpan: verhit boter en/of olie en schroei het vlees",
        "rondom. Voeg een beetje water toe en stoof het vlees op een lage tempera-",
        "tuur gedurende 150-180 minuten. Let op, geef schadelijke bacteriën geen",
        "Gemiddelde voedingswaarde per 100 g: energie 693 kJ/kcal 166,",
        "vetten 9,5 g, waarvan verzadigde vetzuren 4,2 g, koolhydraten 0,0 g,",
        "waarvan suikers 0,0 g, vezels 0,0 g, eiwitten 20,1 g, zout 0,18 g.",
        "Bewaaradvies: zo spoedig mogelijk na aankoop consumeren, uiterlijk op",
        "de vermelde houdbaarheidsdatum, mits gekoeld bewaard (max. 7°C).",
    )
    private val riblapValues =
        LabelValues(kcal = 166.0, protein = 20.1, carbs = 0.0, fat = 9.5, fibre = 0.0, sugar = 0.0, salt = 0.18)

    @Test fun `values written as running text instead of a table`() {
        assertEquals(riblapValues, NutritionLabelParser.parse(text(*riblap)))
    }

    @Test fun `running text in a photo taken sideways`() {
        assertEquals(riblapValues, NutritionLabelParser.parse(text(*riblap, sideways = true)))
    }

    @Test fun `running text with sugar before fibre in one line takes each its own value`() {
        val v = NutritionLabelParser.parse(
            text("Per 100 g: energie 1500 kJ / 358 kcal, vet 2 g, koolhydraten 70 g, vezels 3 g, suikers 2 g, eiwit 9 g, zout 0,5 g"),
        )
        assertEquals(LabelValues(kcal = 358.0, protein = 9.0, carbs = 70.0, fat = 2.0, fibre = 3.0, sugar = 2.0, salt = 0.5), v)
    }

    @Test fun `wine bottle with only energy per 100 ml`() {
        val v = NutritionLabelParser.parse(text("75 Cl.", "14 % Vol.", "E (100 ml) 317 kJ/76 kcal", "CONTIENE SULFITOS"))
        assertEquals(76.0, v.kcal)
        assertTrue(v.perMl)
        assertEquals(1, v.foundCount)
    }

    @Test fun `kcal written before the number, and a kJ and kcal pair`() {
        assertEquals(166.0, NutritionLabelParser.parse(text("Energie 693 kJ / kcal 166")).kcal)
        assertEquals(250.0, NutritionLabelParser.parse(text("Energie (kJ/kcal) 1046/250")).kcal)
        assertEquals(250.0, NutritionLabelParser.parse(text("Energie kcal/kJ 250/1046")).kcal)
    }

    @Test fun `values in one line without commas`() {
        val v = NutritionLabelParser.parse(text("Per 100 g: Energie 1046 kJ / 250 kcal Vet 10 g Koolhydraten 30 g Eiwit 5 g Zout 1,1 g"))
        assertEquals(LabelValues(kcal = 250.0, protein = 5.0, carbs = 30.0, fat = 10.0, salt = 1.1), v)
    }

    @Test fun `each name with its value on the next line`() {
        val v = NutritionLabelParser.parse(text("Voedingswaarde per 100 g", "Energie", "250 kcal", "Vet", "10 g", "Koolhydraten", "30 g", "Eiwit", "5 g"))
        assertEquals(LabelValues(kcal = 250.0, protein = 5.0, carbs = 30.0, fat = 10.0), v)
    }

    @Test fun `words broken over two lines with a hyphen`() {
        val v = NutritionLabelParser.parse(
            text("per 100 g: energie 1500 kJ/358 kcal, vetten 2 g, koolhy-", "draten 70 g, eiwit-", "ten 9 g, zout 0,5 g."),
        )
        assertEquals(LabelValues(kcal = 358.0, protein = 9.0, carbs = 70.0, fat = 2.0, salt = 0.5), v)
    }

    // AH sperziebonen: "vetten, waarvan" is the fat row itself, not a sub-row.
    @Test fun `vetten waarvan and koolhydraten waarvan rows`() {
        val v = NutritionLabelParser.parse(
            table(
                row("Voedingswaarde per" to 0f, "100 g" to 300f, "portie (200 g)" to 500f),
                row("energie" to 0f, "106 kJ / 25 kcal" to 300f, "212 kJ / 51 kcal" to 500f),
                row("vetten, waarvan" to 0f, "0,2 g" to 300f, "0,4 g" to 500f),
                row("- verzadigde vetzuren" to 0f, "0,1 g" to 300f, "0,2 g" to 500f),
                row("- onverzadigde vetzuren" to 0f, "0,1 g" to 300f, "0,2 g" to 500f),
                row("koolhydraten, waarvan" to 0f, "1,8 g" to 300f, "3,6 g" to 500f),
                row("- suikers" to 0f, "1,2 g" to 300f, "2,4 g" to 500f),
                row("vezels" to 0f, "3,6 g" to 300f, "7,2 g" to 500f),
                row("eiwitten" to 0f, "2,3 g" to 300f, "4,6 g" to 500f),
                row("zout" to 0f, "0 g" to 300f, "0 g" to 500f),
            ),
        )
        assertEquals(LabelValues(kcal = 25.0, protein = 2.3, carbs = 1.8, fat = 0.2, fibre = 3.6, sugar = 1.2, salt = 0.0), v)
    }

    @Test fun `an upside-down read that finds nothing is replaced by the turned read`() {
        val nonsense = text("6 0 1noz 6 L'0 uelmie", "'6 L'L Sjayins ueniem")
        val turned = text("per 100 g: energie 173 kJ / 41 kcal, vetten 0,2 g, koolhydraten 7,7 g, eiwitten 0,7 g")
        assertTrue(needsTurning(nonsense))
        assertEquals(turned, readLabelText(nonsense) { turned })
        assertEquals(turned, readLabelText(turned) { error("no second read needed") })
    }
}
