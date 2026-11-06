package nl.guido.foodtracker.feature.sport

import nl.guido.foodtracker.core.model.SportEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SportImportTest {
    private val start = Instant.parse("2026-11-05T23:30:00Z") // 00:30 on 6 Nov in Amsterdam
    private fun session(
        id: String = "rec-1", title: String? = null, active: Double? = 312.4, total: Double? = 400.0,
        offset: ZoneOffset? = ZoneOffset.ofHours(1),
    ) = ImportedSession(id, start, start.plusSeconds(35 * 60 + 20), offset, title, "Hardlopen", active, total)

    @Test
    fun `a Garmin run becomes a sport line`() {
        val e = SportImport.toEntry(session(), "me", emptySet())!!
        assertEquals("Hardlopen", e.name)
        assertEquals(312, e.kcal)
        assertEquals(35, e.minutes)
        assertEquals(LocalDate.of(2026, 11, 6), e.date)
        assertEquals("rec-1", e.externalId)
        assertEquals(SportEntry.importedId("rec-1"), e.id)
    }

    @Test
    fun `own title wins, total kcal only without active`() {
        val e = SportImport.toEntry(session(title = " Ochtendloop ", active = null), "me", emptySet())!!
        assertEquals("Ochtendloop", e.name)
        assertEquals(400, e.kcal)
    }

    @Test
    fun `skipped when already imported or without kcal`() {
        assertNull(SportImport.toEntry(session(), "me", setOf("rec-1")))
        assertNull(SportImport.toEntry(session(active = null, total = null), "me", emptySet()))
        assertNull(SportImport.toEntry(session(active = 0.2, total = null), "me", emptySet()))
    }

    @Test
    fun `phone zone when the session has no offset`() {
        val e = SportImport.toEntry(session(offset = null), "me", emptySet(), zone = ZoneId.of("UTC"))!!
        assertEquals(LocalDate.of(2026, 11, 5), e.date)
    }
}
