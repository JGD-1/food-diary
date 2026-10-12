package nl.guido.foodtracker.feature.recipes.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteLinesTest {
    private fun line(text: String) = parseNoteLine(text)!!

    @Test
    fun `grams and kilos, with or without a space`() {
        line("300 g gehakt").let { assertEquals("gehakt", it.food); assertEquals(300.0, it.grams()!!, 0.01) }
        line("300g gehakt").let { assertEquals("gehakt", it.food); assertEquals(300.0, it.grams()!!, 0.01) }
        line("500 gr. kipfilet").let { assertEquals("kipfilet", it.food); assertEquals(500.0, it.grams()!!, 0.01) }
        line("1,5 kg aardappelen").let { assertEquals("aardappelen", it.food); assertEquals(1500.0, it.grams()!!, 0.01) }
        assertNull(line("300 g gehakt").amountLabel)
    }

    @Test
    fun `liquids in ml, dl and litres count as grams`() {
        assertEquals(250.0, line("250 ml melk").grams()!!, 0.01)
        assertEquals(200.0, line("2 dl room").grams()!!, 0.01)
        assertEquals(500.0, line("½ l bouillon").grams()!!, 0.01)
        assertEquals("bouillon", line("½ l bouillon").food)
    }

    @Test
    fun `the amount may come after the food`() {
        line("gehakt 500 g").let { assertEquals("gehakt", it.food); assertEquals(500.0, it.grams()!!, 0.01) }
        line("olijfolie 2 el").let { assertEquals("olijfolie", it.food); assertEquals(30.0, it.grams()!!, 0.01); assertEquals("2 el", it.amountLabel) }
        line("eieren 3").let { assertEquals("eieren", it.food); assertEquals(3.0, it.count!!, 0.01) }
    }

    @Test
    fun `pieces use the size of one`() {
        val eggs = line("2 eieren")
        assertEquals("eieren", eggs.food)
        assertEquals(100.0, eggs.grams(pieceGrams = 50.0)!!, 0.01)
        assertEquals("2 eieren", eggs.amountLabel)
        // Unknown size: weigh or type later.
        assertNull(eggs.grams())
        assertEquals(60.0, line("1 stuk paprika").grams(pieceGrams = 60.0)!!, 0.01)
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
        assertEquals("½ ui", line("½ ui").amountLabel)
    }

    @Test
    fun `spoons, cloves, pinches, tins and packs`() {
        assertEquals(10.0, line("2 tl paprikapoeder").grams()!!, 0.01)
        assertEquals(15.0, line("3 teentjes knoflook").grams()!!, 0.01)
        assertEquals("knoflook", line("3 teentjes knoflook").food)
        assertEquals(1.0, line("snufje zout").grams() ?: -1.0, 0.01)
        assertEquals(400.0, line("1 blik tomaten").grams()!!, 0.01)
        assertEquals(800.0, line("2 blikken tomaten").grams(packageG = 400.0)!!, 0.01)
        assertEquals("1 blik", line("1 blik tomaten").amountLabel)
        assertEquals(250.0, line("1 pak spaghetti").grams(packageG = 250.0)!!, 0.01)
        assertNull(line("1 pak spaghetti").grams())
        assertEquals(30.0, line("2 tbsp olive oil").grams()!!, 0.01)
        assertEquals("olive oil", line("2 tbsp olive oil").food)
    }

    @Test
    fun `misread digits are fixed`() {
        assertEquals(300.0, line("3OO g gehakt").grams()!!, 0.01)
        assertEquals(100.0, line("l00 ml melk").grams()!!, 0.01)
        assertEquals(1000.0, line("1 l melk").grams()!!, 0.01)
        assertEquals(10_000.0, line("10l water").grams()!!, 0.01)
        assertEquals("100", fixDigits("1OO"))
        assertEquals("Olie", fixDigits("Olie"))
    }

    @Test
    fun `bullets, numbering, notes and filler words are left out`() {
        line("- 300 g kip").let { assertEquals("kip", it.food); assertEquals(300.0, it.grams()!!, 0.01) }
        line("• 2 eieren").let { assertEquals("eieren", it.food) }
        line("1. 300 g kip").let { assertEquals("kip", it.food); assertEquals(300.0, it.grams()!!, 0.01) }
        assertEquals("ui", line("1 ui, gesnipperd").food)
        assertEquals("paprika", line("2 paprika (rood)").food)
        assertEquals("kip", line("300 g van de kip").food)
        assertEquals("tomatoes", line("2 cans of tomatoes").food)
    }

    @Test
    fun `a line without an amount is still a food`() {
        line("zout").let { assertEquals("zout", it.food); assertNull(it.count); assertNull(it.grams()); assertNull(it.amountLabel) }
        line("verse peterselie").let { assertEquals("verse peterselie", it.food) }
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
