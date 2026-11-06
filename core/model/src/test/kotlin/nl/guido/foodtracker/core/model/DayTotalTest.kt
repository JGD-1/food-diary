package nl.guido.foodtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DayTotalTest {
    private val day = LocalDate.of(2026, 11, 6)

    @Test fun idIsTheSameForTheSamePersonAndDay() {
        assertEquals(DayTotal("a", day, 100, 2000).id, DayTotal("a", day, 900, null).id)
        assertNotEquals(DayTotal("a", day, 100, 2000).id, DayTotal("b", day, 100, 2000).id)
        assertNotEquals(DayTotal("a", day, 100, 2000).id, DayTotal("a", day.plusDays(1), 100, 2000).id)
    }

    @Test fun fractionIsShareOfOwnTarget() {
        assertEquals(0.5f, DayTotal("a", day, 1000, 2000).fraction!!, 0.0001f)
        assertEquals(1.25f, DayTotal("a", day, 2500, 2000).fraction!!, 0.0001f)
        assertNull(DayTotal("a", day, 1000, null).fraction)
        assertNull(DayTotal("a", day, 1000, 0).fraction)
    }
}
