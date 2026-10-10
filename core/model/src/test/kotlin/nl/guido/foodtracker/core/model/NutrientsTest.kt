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

    @Test
    fun fibreSugarSaltAreUnknownUntilSomeoneKnowsThem() {
        val plain = Nutrients(100.0, 1.0, 2.0, 3.0)
        assertEquals(null, (plain + plain).fibre)
        val withFibre = plain.copy(fibre = 4.0, salt = 0.5)
        val sum = listOf(plain, withFibre, withFibre).sum()
        assertEquals(8.0, sum.fibre!!, 0.0)
        assertEquals(1.0, sum.salt!!, 0.0)
        assertEquals(null, sum.sugar)
        assertEquals(2.0, withFibre.forGrams(50.0).fibre!!, 0.0)
        assertEquals(plain, plain + Nutrients.ZERO)
    }

    @Test
    fun oldIngredientJsonWithoutFibreStillReads() {
        val json = """{"foodId":"oats","name":"Oats","grams":40.0,"per100g":{"kcal":370.0,"protein":13.0,"carbs":60.0,"fat":7.0}}"""
        val ingredient = Json.decodeFromString(Ingredient.serializer(), json)
        assertEquals(null, ingredient.per100g.fibre)
        // And a value without fibre writes the same JSON as before.
        assertEquals(json, Json.encodeToString(Ingredient.serializer(), ingredient))
    }

    @Test
    fun gramsLeftCountsEveryonesPortionsOfThisBatch() {
        val batch = Batch("b", "h", null, "Chili", listOf(oats), cookedWeightG = 1000.0, cookedOn = LocalDate.of(2026, 10, 9))
        val portions = listOf(
            BatchPortion("p1", "b", "h", "guido", 350.0),
            BatchPortion("p2", "b", "h", "partner", 400.0),
            BatchPortion("p3", "other", "h", "guido", 500.0),
        )
        assertEquals(250.0, batch.gramsLeft(portions), 0.0)
        assertEquals(0.0, batch.gramsLeft(portions + BatchPortion("p4", "b", "h", "guido", 900.0)), 0.0)
    }
}
