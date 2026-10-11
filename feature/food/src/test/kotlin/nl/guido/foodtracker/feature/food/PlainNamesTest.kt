package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.names.BundledFoodNames
import nl.guido.foodtracker.feature.food.names.SHORT_FORMS_ASSET
import nl.guido.foodtracker.feature.food.names.ShortFormsParser
import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** S1: the app's plain line under NEVO names; the NEVO name itself stays as it is. */
class PlainNamesTest {
    private val forms = File("src/main/assets/$SHORT_FORMS_ASSET").bufferedReader().use { ShortFormsParser.parse(it) }
    private val en = forms.getValue("en")
    private val nl = forms.getValue("nl")

    @Test fun shortFormsWrittenOut() {
        assertEquals("without skin, average", en.plainLine("Apple wo skin av"))
        assertEquals("with skin, average", en.plainLine("Apple w skin av"))
        assertEquals("average", en.plainLine("Kiwi fruit av"))
        assertEquals("liquid 97% fat <17g saturated fatty acids unsalted", en.plainLine("Cooking fat liq 97% fat <17g sat fa unsalted"))
        assertEquals("with fibre, vitamin and minerals", en.plainLine("Biscuit multigrain several flavours fortified w fibre, vit and min"))
        assertEquals("prepared with semi-skimmed milk without fat", en.plainLine("Potatoes mashed fresh prep w s-sk milk wo fat"))
    }

    @Test fun dutchShortForms() {
        assertEquals("met schil, gemiddeld", nl.plainLine("Appel m schil gem"))
        assertEquals("zonder schil, gemiddeld", nl.plainLine("Appel z schil gem"))
        assertEquals("gemiddelde van fijn en grof", nl.plainLine("Tarwebrood volkoren gem v fijn en grof"))
        assertEquals(
            "vloeibaar 80% vet <17 g verzadigde vetzuren ongezouten",
            nl.plainLine("Margarine vlb 80% vet <17 g verz vetz ongezouten"),
        )
    }

    @Test fun noLineWithoutShortForms() {
        assertNull(en.plainLine("Banana"))
        assertNull(nl.plainLine("Melk halfvolle"))
        // Dutch compound marks ("kippen-") are no short forms.
        assertNull(nl.plainLine("Ei kippen- gebakken"))
    }

    @Test fun manyNevoNamesGetALineAndNoneIsChanged() {
        val file = File("src/main/assets/$NEVO_ASSET")
        val english = file.bufferedReader().use { NevoParser.parse(it) }
        val dutch = file.bufferedReader().use { NevoParser.parse(it, dutch = true) }
        val names = BundledFoodNames({ forms }, { "en" })
        val lines = english.mapNotNull { names.plain(it) }
        assertTrue("${lines.size} English lines", lines.size > 500)
        assertTrue(lines.none { it.isBlank() || it.startsWith(",") })
        assertTrue(dutch.mapNotNull { BundledFoodNames({ forms }, { "nl" }).plain(it) }.size > 500)
        // plain() never touches the food: the NEVO name stays the title.
        val apple = english.first { it.name == "Apple w skin av" }
        names.plain(apple)
        assertEquals("Apple w skin av", apple.name)
    }

    @Test fun onlyForNevoFoods() {
        val names = BundledFoodNames({ forms }, { "en" })
        assertNull(names.plain(food("Yoghurt w fruit", source = FoodOrigin.OFF)))
        assertEquals("with fruit", names.plain(food("Yoghurt w fruit")))
    }
}
