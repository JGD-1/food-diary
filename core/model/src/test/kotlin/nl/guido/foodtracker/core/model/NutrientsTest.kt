package nl.guido.foodtracker.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NutrientsTest {
    private val oats = Ingredient("oats", "Oats", 400.0, Nutrients(370.0, 13.0, 60.0, 7.0))
    private val milk = Ingredient("milk", "Milk", 600.0, Nutrients(50.0, 3.5, 4.8, 1.8))

    @Test
    fun forGramsScalesPer100g() {
        assertEquals(185.0, Nutrients(370.0, 13.0, 60.0, 7.0).forGrams(50.0).kcal, 0.001)
    }

    @Test
    fun batchShareIsProportionalToCookedWeight() {
        val batch = Batch("b", "h", null, "Porridge", listOf(oats, milk), cookedWeightG = 900.0, cookedOn = LocalDate.of(2026, 10, 9))
        // Total 1480 + 300 = 1780 kcal in 900 g cooked; a 300 g portion is a third.
        assertEquals(1780.0 / 3, batch.shareFor(300.0).kcal, 0.001)
    }

    @Test
    fun batchWithNoWeightGivesZero() {
        val batch = Batch("b", "h", null, "Empty", listOf(oats), cookedWeightG = 0.0, cookedOn = LocalDate.of(2026, 10, 9))
        assertEquals(0.0, batch.shareFor(100.0).kcal, 0.0)
    }

    @Test
    fun loggedSurvivesJsonRoundTrip() {
        val logged: Logged = Logged.RecipeRef("r", "Chili", extras = listOf(oats))
        val json = Json.encodeToString(Logged.serializer(), logged)
        assertEquals(logged, Json.decodeFromString(Logged.serializer(), json))
    }
}
