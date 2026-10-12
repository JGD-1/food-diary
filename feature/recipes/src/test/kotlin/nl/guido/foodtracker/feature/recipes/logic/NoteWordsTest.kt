package nl.guido.foodtracker.feature.recipes.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteWordsTest {
    @Test
    fun `a word picked once is found again, whatever its capitals`() {
        val words = NoteWords().learn("Kbonen", "kidney")
        assertEquals("kidney", words.lookup("kbonen"))
        assertEquals("kidney", words.lookup("KBonen"))
        assertNull(words.lookup("bonen"))
    }

    @Test
    fun `a word read slightly differently is close to what was learned`() {
        val words = NoteWords().learn("kbonen", "kidney")
        assertEquals("kidney", words.closest("kbonon"))
        assertNull(words.closest("gehakt"))
    }

    @Test
    fun `the newest pick wins and the list stays small`() {
        var words = NoteWords().learn("kbonen", "kidney").learn("kbonen", "brown")
        assertEquals("brown", words.lookup("kbonen"))
        repeat(NoteWords.MAX_WORDS + 5) { words = words.learn("word$it", "f$it") }
        assertEquals(NoteWords.MAX_WORDS, words.foods.size)
        assertNull(words.lookup("kbonen"))
        assertEquals("f504", words.lookup("word504"))
    }

    @Test
    fun `learned words survive being kept in a file`() {
        val words = NoteWords().learn("kbonen", "kidney").learn("rvlees", "beef")
        assertEquals(words, NoteWords.decode(words.encode()))
        assertEquals(NoteWords(), NoteWords.decode("not json"))
        assertEquals(NoteWords(), NoteWords.decode(null))
    }

    @Test
    fun `shorthand is a letter or two glued to a word`() {
        assertEquals(listOf("k" to "bonen", "kb" to "onen"), shorthandTries("Kbonen"))
        assertEquals(listOf("r" to "vlees", "rv" to "lees"), shorthandTries("Rvlees"))
        assertTrue(shorthandTries("rode ui").isEmpty())
        assertTrue(shorthandTries("ui").isEmpty())
    }

    @Test
    fun `a food name fits shorthand when it holds the word and one starting with the letters`() {
        assertTrue(fitsShorthand("k", "bonen", "Bonen kidney- rode blik/glas"))
        assertTrue(fitsShorthand("k", "bonen", "Kidneybonen"))
        assertTrue(fitsShorthand("k", "bonen", "kidney beans bonen"))
        assertTrue(fitsShorthand("r", "vlees", "Rundvlees mager rauw"))
        assertFalse(fitsShorthand("k", "bonen", "Bonen bruine gekookt"))
        assertFalse(fitsShorthand("k", "bonen", "Kikkererwten"))
        assertFalse(fitsShorthand("kb", "onen", "Bonen kidney- rode blik/glas"))
    }
}
