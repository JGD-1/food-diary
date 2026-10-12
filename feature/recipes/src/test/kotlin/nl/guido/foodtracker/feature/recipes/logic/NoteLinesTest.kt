package nl.guido.foodtracker.feature.recipes.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteLinesTest {
    private fun line(text: String) = parseNoteLine(text)!!
    private fun grams(text: String, piece: Double? = null, serving: Double? = null, pack: Double? = null) =
        line(text).amount(piece, serving, pack)!!.grams
    private fun label(text: String, piece: Double? = null, pack: Double? = null) = line(text).amount(piece, null, pack)?.label

    @Test
    fun `grams and kilos, with or without a space`() {
        assertEquals("gehakt", line("300 g gehakt").food)
        assertEquals(300.0, grams("300 g gehakt"), 0.01)
        assertEquals(300.0, grams("300g gehakt"), 0.01)
        assertEquals("kipfilet", line("500 gr. kipfilet").food)
        assertEquals(500.0, grams("500 gr. kipfilet"), 0.01)
        assertEquals(1500.0, grams("1,5 kg aardappelen"), 0.01)
        assertNull(label("300 g gehakt"))
    }

    @Test
    fun `a number without a unit - hundreds are grams, commas and small numbers are kilos`() {
        assertEquals(500.0, grams("500 gehakt"), 0.01)
        assertEquals(1200.0, grams("1200 aardappelen"), 0.01)
        assertEquals(1500.0, grams("1,5 aardappelen"), 0.01)
        assertEquals(500.0, grams("0.5 kipfilet"), 0.01)
        assertEquals(2000.0, grams("2 gehakt"), 0.01)
        assertEquals(500.0, grams("gehakt 500"), 0.01)
        assertEquals(1500.0, grams("aardappelen 1,5"), 0.01)
        // Even for a food that comes in pieces, a comma means kilos.
        assertEquals(1500.0, grams("1,5 uien", piece = 150.0), 0.01)
        assertNull(label("500 gehakt"))
        assertNull(label("1,5 aardappelen"))
        assertNull(label("2 gehakt"))
    }

    @Test
    fun `small numbers are pieces for foods that come in pieces`() {
        assertEquals("eieren", line("2 eieren").food)
        assertEquals(100.0, grams("2 eieren", piece = 50.0), 0.01)
        assertEquals("2 eieren", label("2 eieren", piece = 50.0))
        assertEquals(60.0, grams("1 paprika", serving = 60.0), 0.01)
        assertEquals(60.0, grams("1 stuk paprika", piece = 60.0), 0.01)
        assertEquals(3.0, line("eieren 3").count!!, 0.01)
        assertEquals(150.0, grams("eieren 3", piece = 50.0), 0.01)
        // "2 stuks" with no known piece size: weigh or type later.
        assertNull(line("2 stuks paprika").amount())
    }

    @Test
    fun `liquids in ml, dl and litres count as grams`() {
        assertEquals(250.0, grams("250 ml melk"), 0.01)
        assertEquals(200.0, grams("2 dl room"), 0.01)
        assertEquals(500.0, grams("½ l bouillon"), 0.01)
        assertEquals("bouillon", line("½ l bouillon").food)
    }

    @Test
    fun `the amount may come after the food`() {
        assertEquals("gehakt", line("gehakt 500 g").food)
        assertEquals(500.0, grams("gehakt 500 g"), 0.01)
        assertEquals("olijfolie", line("olijfolie 2 el").food)
        assertEquals(30.0, grams("olijfolie 2 el"), 0.01)
        assertEquals("2 el", label("olijfolie 2 el"))
    }

    @Test
    fun `fractions and number words`() {
        assertEquals(0.5, line("½ ui").count!!, 0.001)
        assertEquals(0.5, line("1/2 ui").count!!, 0.001)
        assertEquals(1.5, line("1 1/2 ui").count!!, 0.001)
        assertEquals(1.5, line("1½ ui").count!!, 0.001)
        assertEquals(0.5, line("halve ui").count!!, 0.001)
        assertEquals(2.0, line("twee uien").count!!, 0.001)
        assertEquals("uien", line("twee uien").food)
        assertEquals("½ ui", label("½ ui", piece = 150.0))
        assertEquals(75.0, grams("½ ui", piece = 150.0), 0.01)
    }

    @Test
    fun `spoons, cloves, pinches, tins and packs`() {
        assertEquals(10.0, grams("2 tl paprikapoeder"), 0.01)
        assertEquals(15.0, grams("3 teentjes knoflook"), 0.01)
        assertEquals("knoflook", line("3 teentjes knoflook").food)
        assertEquals(1.0, grams("snufje zout"), 0.01)
        assertEquals(400.0, grams("1 blik tomaten"), 0.01)
        assertEquals(800.0, grams("2 blikken tomaten", pack = 400.0), 0.01)
        assertEquals("1 blik", label("1 blik tomaten"))
        assertEquals(250.0, grams("1 pak spaghetti", pack = 250.0), 0.01)
        assertNull(line("1 pak spaghetti").amount())
        assertEquals(30.0, grams("2 tbsp olive oil"), 0.01)
        assertEquals("olive oil", line("2 tbsp olive oil").food)
    }

    @Test
    fun `misread digits are fixed`() {
        assertEquals(300.0, grams("3OO g gehakt"), 0.01)
        assertEquals(100.0, grams("l00 ml melk"), 0.01)
        assertEquals(300.0, grams("3OO gehakt"), 0.01)
        assertEquals(1000.0, grams("1 l melk"), 0.01)
        assertEquals(10_000.0, grams("10l water"), 0.01)
        assertEquals("100", fixDigits("1OO"))
        assertEquals("Olie", fixDigits("Olie"))
    }

    @Test
    fun `bullets, numbering, notes and filler words are left out`() {
        assertEquals("kip", line("- 300 g kip").food)
        assertEquals(300.0, grams("- 300 g kip"), 0.01)
        assertEquals("eieren", line("• 2 eieren").food)
        assertEquals("kip", line("1. 300 g kip").food)
        assertEquals(300.0, grams("1. 300 g kip"), 0.01)
        assertEquals("ui", line("1 ui, gesnipperd").food)
        assertEquals("paprika", line("2 paprika (rood)").food)
        assertEquals("kip", line("300 g van de kip").food)
        assertEquals("tomatoes", line("2 cans of tomatoes").food)
    }

    @Test
    fun `a line without an amount is still a food`() {
        val salt = line("zout")
        assertEquals("zout", salt.food)
        assertNull(salt.count)
        assertNull(salt.amount())
        assertEquals("verse peterselie", line("verse peterselie").food)
    }

    @Test
    fun `headings, numbers and smudges are not ingredients`() {
        assertNull(parseNoteLine("Ingrediënten:"))
        assertNull(parseNoteLine("Ingredients"))
        assertNull(parseNoteLine("300"))
        assertNull(parseNoteLine("300 g"))
        assertNull(parseNoteLine("- x"))
        assertNull(parseNoteLine("   "))
    }

    @Test
    fun `counts in labels`() {
        assertEquals("½", formatCount(0.5))
        assertEquals("2", formatCount(2.0))
        assertEquals("1½", formatCount(1.5))
    }
}
