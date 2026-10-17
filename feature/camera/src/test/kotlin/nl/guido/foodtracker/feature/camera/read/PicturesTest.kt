package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PicturesTest {
    private fun scores(vararg pairs: Pair<Int, Float>) = FloatArray(1000).apply { pairs.forEach { (i, s) -> this[i] = s } }

    @Test fun `only fruit and vegetables the model is fairly sure of are searched`() {
        assertEquals(listOf("sweet pepper", "cucumber"), Produce.likely(scores(945 to 0.6f, 943 to 0.3f, 1 to 0.9f)))
        assertTrue(Produce.likely(scores(945 to 0.1f)).isEmpty())
        assertEquals(listOf("pumpkin"), Produce.likely(scores(940 to 0.4f, 942 to 0.3f))) // three squashes, one word
        assertEquals("#sweetpepper", Produce.token("sweet pepper"))
    }

    @Test fun `the most alike food comes first, and unlike pictures find nothing`() {
        val memory = PictureMemory()
            .learn("onion", floatArrayOf(1f, 0f, 0f))
            .learn("pepper", floatArrayOf(0f, 1f, 0f))
        assertEquals(listOf("onion"), memory.nearest(floatArrayOf(2f, 0.3f, 0f))) // length doesn't matter
        assertTrue(memory.nearest(floatArrayOf(0f, 0f, 1f)).isEmpty())
    }

    @Test fun `examples are kept per food and survive saving`() {
        val many = (1..PictureMemory.PER_FOOD + 3).fold(PictureMemory()) { m, i -> m.learn("onion", floatArrayOf(1f, i.toFloat())) }
            .learn("pepper", floatArrayOf(0f, 1f))
        assertEquals(PictureMemory.PER_FOOD, many.examples.count { it.foodId == "onion" })
        val again = PictureMemory.decode(many.encode())
        assertEquals(many.examples.size, again.examples.size)
        assertEquals(listOf("pepper"), again.nearest(floatArrayOf(0f, 1f), limit = 1))
        assertTrue(PictureMemory.decode(byteArrayOf(1, 2, 3)).examples.isEmpty())
    }

    /** Two dimensions, three kinds: onion leans on the first number, pepper on the second, "no food" on neither. */
    private val head = HeadStart(
        words = listOf("onions", "sweet pepper", ""),
        weights = floatArrayOf(1f, -1f, 0f, -1f, 1f, 0f),
        bias = floatArrayOf(0f, 0f, 0.5f),
        scale = 5f,
    )

    @Test fun `the head start names the likely kind, and nothing when it sees no food`() {
        assertEquals("onions", head.kinds(floatArrayOf(3f, 0.2f)).first())
        assertEquals("sweet pepper", head.kinds(floatArrayOf(0.1f, 1f)).first())
        val nothing = HeadStart(listOf("onions", ""), floatArrayOf(0f, 0f), floatArrayOf(0f, 3f), 1f)
        assertTrue(nothing.kinds(floatArrayOf(1f)).isEmpty())
        assertTrue(head.kinds(floatArrayOf(1f, 2f, 3f)).isEmpty()) // wrong size
    }

    @Test fun `the head start file is read back as written by the tool`() {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(1); out.writeInt(3); out.writeInt(2); out.writeFloat(5f)
            listOf("onions", "sweet pepper", "").forEach(out::writeUTF)
            floatArrayOf(1f, -1f, 0f, -1f, 1f, 0f).forEach(out::writeFloat)
            floatArrayOf(0f, 0f, 0.5f).forEach(out::writeFloat)
        }
        val read = HeadStart.decode(bytes.toByteArray())!!
        assertEquals(listOf("onions", "sweet pepper", ""), read.words)
        assertEquals("onions", read.kinds(floatArrayOf(3f, 0.2f)).first())
        assertEquals(null, HeadStart.decode(byteArrayOf(0, 0, 0, 9)))
    }
}
