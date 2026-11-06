package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.SharedDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PartnerDayTest {
    private val day = LocalDate.of(2026, 11, 6)

    @Test
    fun `shows first name, kcal and share of their own target`() {
        val p = partnerDay(listOf(SharedDay("p", day, 1240, 2000)), listOf(HouseholdMember("p", "Sanne de Vries")))!!
        assertEquals("Sanne", p.name)
        assertEquals(1240, p.kcal)
        assertEquals(0.62f, p.fraction!!, 0.0001f)
    }

    @Test
    fun `nothing for a day without kcal or when nobody shares`() {
        assertNull(partnerDay(listOf(SharedDay("p", day, 0, 2000)), emptyList()))
        assertNull(partnerDay(emptyList(), listOf(HouseholdMember("p", "Sanne"))))
    }

    @Test
    fun `blank name falls back to no name`() {
        assertNull(partnerDay(listOf(SharedDay("p", day, 500, null)), listOf(HouseholdMember("p", " ")))!!.name)
    }
}
