package nl.guido.foodtracker.feature.sport

import nl.guido.foodtracker.core.model.SportEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SportLogicTest {
    // Friday 6 Nov 2026; that week starts Monday 2 Nov.
    private val today = LocalDate.of(2026, 11, 6)

    private fun sport(id: String, day: LocalDate, kcal: Int, minutes: Int? = null, at: Long = 0) =
        SportEntry(id, "me", day, "Hardlopen", kcal, Instant.ofEpochMilli(at), minutes)

    @Test
    fun `today, this week and earlier`() {
        val entries = listOf(
            sport("a", today, 300, 30, at = 2),
            sport("b", today, 100, null, at = 1),
            sport("c", today.minusDays(4), 400, 45), // Monday: this week
            sport("d", today.minusDays(5), 200, 20), // Sunday before: last week
            sport("e", today.minusDays(20), 999),    // too old for "earlier"
        )
        val days = SportLogic.days(entries, today)
        assertEquals(listOf("b", "a"), days.today.map { it.id })
        assertEquals(SportSummary(2, 30, 400), days.todaySummary)
        assertEquals(SportSummary(3, 75, 800), days.week)
        assertEquals(listOf("c", "d"), days.earlier.map { it.id })
    }

    @Test
    fun `week starts on Monday and loading covers it`() {
        assertEquals(LocalDate.of(2026, 11, 2), SportLogic.weekStart(today))
        assertEquals(LocalDate.of(2026, 11, 2), SportLogic.weekStart(LocalDate.of(2026, 11, 2)))
        assertEquals(today.minusDays(14), SportLogic.firstDayLoaded(today))
    }

    @Test
    fun `form needs a name and kcal, minutes optional`() {
        assertTrue(SportForm(" Hardlopen ", "", "300").canSave)
        assertEquals("Hardlopen", SportForm(" Hardlopen ", "", "300").cleanName)
        assertNull(SportForm("Fietsen", "", "300").minutesValue)
        assertEquals(45, SportForm("Fietsen", "45", "300").minutesValue)
        assertFalse(SportForm("", "", "300").canSave)
        assertFalse(SportForm("Zwemmen", "", "").canSave)
        assertFalse(SportForm("Zwemmen", "", "0").canSave)
        assertFalse(SportForm("Zwemmen", "", "3001").canSave)
        assertFalse(SportForm("Zwemmen", "abc", "300").canSave)
        assertTrue(SportForm("Zwemmen", "", "3000").canSave)
    }

    @Test
    fun `form fills from an entry`() {
        assertEquals(SportForm("Hardlopen", "30", "300"), SportForm.of(sport("a", today, 300, 30)))
        assertEquals(SportForm("Hardlopen", "", "300"), SportForm.of(sport("a", today, 300)))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals(1 to 10, hoursAndMinutes(70))
        assertEquals(0 to 45, hoursAndMinutes(45))
    }
}
