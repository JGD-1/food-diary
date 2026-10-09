package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Test

class StabilizerTest {
    @Test fun `needs the same value several frames in a row`() {
        val s = Stabilizer<Double>(3)
        assertEquals(null, s.offer(245.0))
        assertEquals(null, s.offer(245.0))
        assertEquals(245.0, s.offer(245.0))
    }

    @Test fun `a different value starts again`() {
        val s = Stabilizer<Double>(2)
        s.offer(245.0)
        assertEquals(null, s.offer(246.0))
        assertEquals(246.0, s.offer(246.0))
    }

    @Test fun `unreadable frames are skipped, not counted`() {
        val s = Stabilizer<Double>(2)
        s.offer(245.0)
        assertEquals(null, s.offer(null))
        assertEquals(245.0, s.offer(245.0))
    }
}
