package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileFormTest {
    private val filled = ProfileForm(
        birthYear = "1990", sex = Sex.MALE, heightCm = "180", weightKg = "85,0",
        activity = ActivityLevel.LIGHT, targetWeightKg = "78", weeklyPaceKg = 0.5,
    )

    @Test
    fun completeFormMakesAProfile() {
        val profile = filled.toProfile("u", "Guido", MONDAY, existing = null)!!
        assertEquals(85.0, profile.startWeightKg, 0.0)
        assertEquals(78.0, profile.targetWeightKg, 0.0)
        assertEquals("Guido", profile.name)
        assertNull(profile.manualTargetKcal)
    }

    @Test
    fun missingOrOddValuesMakeNothing() {
        assertNull(filled.copy(sex = null).toProfile("u", "G", MONDAY, null))
        assertNull(filled.copy(heightCm = "18").toProfile("u", "G", MONDAY, null))
        assertNull(filled.copy(birthYear = "2024").toProfile("u", "G", MONDAY, null))
        assertNull(filled.copy(useOwnTarget = true, ownTargetKcal = "").toProfile("u", "G", MONDAY, null))
    }

    @Test
    fun ownTargetIsKept() {
        val profile = filled.copy(useOwnTarget = true, ownTargetKcal = "2100").toProfile("u", "G", MONDAY, null)!!
        assertEquals(2100, profile.manualTargetKcal)
    }

    @Test
    fun editingKeepsTheStartingWeight() {
        val existing = filled.toProfile("u", "G", MONDAY, null)!!
        val edited = filled.copy(weightKg = "", targetWeightKg = "75").toProfile("u", "G", MONDAY, existing)!!
        assertEquals(85.0, edited.startWeightKg, 0.0)
        assertEquals(75.0, edited.targetWeightKg, 0.0)
    }

    @Test
    fun roundTripsThroughTheForm() {
        val profile = filled.toProfile("u", "G", MONDAY, null)!!
        assertEquals(profile, ProfileForm.from(profile).toProfile("u", "G", MONDAY, profile))
    }

    @Test
    fun parsesCommaOrDot() {
        assertEquals(84.2, parseKg("84,2")!!, 0.0)
        assertEquals(84.2, parseKg(" 84.2 ")!!, 0.0)
        assertNull(parseKg("8"))
    }
}
