package nl.guido.foodtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportEntryTest {
    @Test fun sportRaisesTheDayTarget() {
        assertEquals(2400, SportEntry.dayTarget(2100, 300))
        assertEquals(2100, SportEntry.dayTarget(2100, 0))
        assertEquals(2100, SportEntry.dayTarget(2100, -50))
        assertNull(SportEntry.dayTarget(null, 300))
    }

    @Test fun importedIdIsStable() {
        assertEquals(SportEntry.importedId("rec-1"), SportEntry.importedId("rec-1"))
        org.junit.Assert.assertNotEquals(SportEntry.importedId("rec-1"), SportEntry.importedId("rec-2"))
    }
}
