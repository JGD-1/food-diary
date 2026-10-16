package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackWordsTest {
    @Test fun `a learned pack is found when most of its words are in view`() {
        val words = PackWords().learn(setOf("rode", "kidneybonen", "heinz"), "beans")
        assertEquals(listOf("beans"), words.lookup(setOf("rode", "kidneybonen", "other")))
        assertTrue(words.lookup(setOf("kidneybonen")).isEmpty()) // one of three is not enough
    }

    @Test fun `the newest pick for the same words wins, and it survives saving`() {
        val words = PackWords().learn(setOf("paprika", "rood"), "a").learn(setOf("paprika", "rood"), "b")
        val again = PackWords.decode(words.encode())
        assertEquals(listOf("b"), again.lookup(setOf("paprika", "rood")))
        assertEquals(1, again.packs.size)
    }

    @Test fun `a broken file starts empty and the list stays small`() {
        assertTrue(PackWords.decode("not json").packs.isEmpty())
        val many = (1..PackWords.MAX_PACKS + 5).fold(PackWords()) { w, i -> w.learn(setOf("food$i"), "id$i") }
        assertEquals(PackWords.MAX_PACKS, many.packs.size)
        assertTrue(many.lookup(setOf("food1")).isEmpty()) // the oldest went first
    }
}
