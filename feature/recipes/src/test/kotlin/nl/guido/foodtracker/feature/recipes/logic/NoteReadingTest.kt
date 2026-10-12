package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteReadingTest {
    private fun frame(vararg lines: Pair<String, Float>) = lines.map { (text, y) -> SeenLine(text, y, y + 20f) }

    @Test
    fun `the same line in many frames is one line, the most seen version wins`() {
        val reading = NoteReading()
        reading.offer(frame("300 g gehakt" to 100f, "2 uien" to 140f))
        reading.offer(frame("300 g gehakl" to 105f, "2 uien" to 145f))
        reading.offer(frame("300 g gehakt" to 90f, "2 vien" to 130f))
        reading.offer(frame("300 g gehakt" to 95f, "2 uien" to 135f))
        val lines = reading.lines
        assertEquals(listOf("300 g gehakt", "2 uien"), lines.map { it.text })
        assertEquals(4, lines[0].seen)
        assertEquals("300 g gehakl", lines[0].versions.last())
    }

    @Test
    fun `a line seen once is not shown`() {
        val reading = NoteReading()
        reading.offer(frame("300 g gehakt" to 100f, "smudge here" to 300f))
        reading.offer(frame("300 g gehakt" to 100f))
        assertEquals(listOf("300 g gehakt"), reading.lines.map { it.text })
    }

    @Test
    fun `the order on the note holds when the phone moves and lines go out of view`() {
        val reading = NoteReading()
        reading.offer(frame("300 g gehakt" to 100f, "2 uien" to 140f, "1 blik tomaten" to 180f))
        // Moved down: the first line is out of view, the others are higher on the picture.
        reading.offer(frame("2 uien" to 20f, "1 blik tomaten" to 60f, "zout" to 100f))
        reading.offer(frame("1 blik tomaten" to 30f, "zout" to 70f))
        reading.offer(frame("300 g gehakt" to 300f, "2 uien" to 340f))
        assertEquals(listOf("300 g gehakt", "2 uien", "1 blik tomaten", "zout"), reading.lines.map { it.text })
    }

    @Test
    fun `two different lines in one frame never merge`() {
        val reading = NoteReading()
        repeat(3) { reading.offer(frame("2 uien" to 100f, "3 uien" to 140f)) }
        assertEquals(listOf("2 uien", "3 uien"), reading.lines.map { it.text })
    }

    @Test
    fun `start again forgets everything`() {
        val reading = NoteReading()
        repeat(3) { reading.offer(frame("zout" to 10f)) }
        reading.clear()
        assertTrue(reading.lines.isEmpty())
    }

    @Test
    fun `similar texts`() {
        assertEquals(1.0, similarity("abc", "abc"), 0.0)
        assertEquals(1, editDistance("gehakt", "gehakl"))
        assertEquals("300 g kip", matchKey(" 300 G  Kip, "))
    }

    private fun food(id: String) = Food(id, id, per100g = Nutrients(100.0, 1.0, 1.0, 1.0), source = FoodOrigin.NEVO)

    @Test
    fun `a food used before wins when near the top of the results`() {
        val results = listOf(food("a"), food("b"), food("c"))
        assertEquals("b", pickFood(results, setOf("b"))!!.id)
        assertEquals("a", pickFood(results, emptySet())!!.id)
        assertEquals("a", pickFood(results, setOf("c"), look = 2)!!.id)
        assertNull(pickFood(emptyList(), setOf("c")))
    }

    @Test
    fun `search tries the whole name first, then each word`() {
        assertEquals(listOf("rode ui gesnipperd", "gesnipperd", "rode"), searchTries("rode ui gesnipperd"))
        assertEquals(listOf("kip"), searchTries("kip"))
    }

    @Test
    fun `a badly read name finds the ingredient used before`() {
        assertEquals("gehakt", closestName("gehakl", listOf("gehakt", "kip")))
        assertNull(closestName("banaan", listOf("gehakt", "kip")))
        assertNull(closestName("ui", listOf("ui")))
    }

    @Test
    fun `checked lines go back to the opening screen unchanged`() {
        val items = listOf(NoteIngredient("f1", 300.0, null), NoteIngredient("f2", null, null), NoteIngredient("f3", 100.0, "2 eieren"))
        assertEquals(items, decodeNoteIngredients(encodeNoteIngredients(items)))
        assertTrue(decodeNoteIngredients("rubbish").isEmpty())
    }
}
