package nl.guido.foodtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SharedDayTest {
    private val day = LocalDate.of(2026, 11, 6)

    @Test fun idIsTheSameForTheSamePersonAndDay() {
        assertEquals(SharedDay("a", day, 100, 2000).id, SharedDay("a", day, 900, null).id)
        assertNotEquals(SharedDay("a", day, 100, 2000).id, SharedDay("b", day, 100, 2000).id)
        assertNotEquals(SharedDay("a", day, 100, 2000).id, SharedDay("a", day.plusDays(1), 100, 2000).id)
    }

    @Test fun fractionIsShareOfOwnTarget() {
        assertEquals(0.5f, SharedDay("a", day, 1000, 2000).fraction!!, 0.0001f)
        assertEquals(1.25f, SharedDay("a", day, 2500, 2000).fraction!!, 0.0001f)
        assertNull(SharedDay("a", day, 1000, null).fraction)
        assertNull(SharedDay("a", day, 1000, 0).fraction)
    }
}
