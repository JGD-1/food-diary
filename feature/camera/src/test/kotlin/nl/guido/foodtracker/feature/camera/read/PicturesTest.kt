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
}
