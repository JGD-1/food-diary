package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackTextTest {
    private fun line(text: String, height: Float) = TextLine(text, 0f, 0f, 400f, height)

    @Test fun `names are plain words, numbers and the nutrition table are left out`() {
        assertEquals("ah rode kidneybonen", packLine("AH Rode Kidneybonen!"))
        assertNull(packLine("Energie 450 kJ / 108 kcal"))
        assertNull(packLine("400 g"))
        assertNull(packLine("Ingrediënten: bonen, water, zout"))
        assertNull(packLine("e"))
    }

    @Test fun `the biggest text seen twice comes first`() {
        val votes = PackTextVotes()
        val frame = listOf(line("Rode kidneybonen", 80f), line("In water", 30f), line("Albert Heijn", 40f))
        votes.offer(frame)
        assertTrue(votes.names().isEmpty())
        votes.offer(frame)
        votes.offer(listOf(line("Misread once", 120f)))
        assertEquals(listOf("rode kidneybonen", "albert heijn", "in water"), votes.names())
        assertEquals(setOf("rode", "kidneybonen"), votes.words()) // shop names and filler are not name words
    }

    @Test fun `searches try the whole name, then its longer words, without shop names and filler`() {
        assertEquals(
            listOf("rode kidneybonen", "paprika rood", "kidneybonen", "paprika", "rode"),
            packSearches(listOf("ah rode kidneybonen in water", "paprika rood")),
        )
    }
}
