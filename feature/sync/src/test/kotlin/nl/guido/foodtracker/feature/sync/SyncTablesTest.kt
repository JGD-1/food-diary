package nl.guido.foodtracker.feature.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.engine.snake
import nl.guido.foodtracker.feature.sync.engine.toLocal
import nl.guido.foodtracker.feature.sync.engine.toRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTablesTest {
    @Test
    fun `column names become snake case online`() {
        assertEquals("cooked_weight_g", snake("cookedWeightG"))
        assertEquals("updated_at", snake("updatedAt"))
        assertEquals("kcal", snake("kcal"))
    }

    @Test
    fun `phone booleans stored as 0 or 1 go online as true or false`() {
        val row = mapOf(
            "id" to "e1", "userId" to "u1", "date" to "2026-10-09", "meal" to "DINNER", "whatJson" to "{}",
            "portionGrams" to 250L, "portionLabel" to null, "kcal" to 400.5, "protein" to 20.0, "carbs" to 30.0,
            "fat" to 10.0, "isEstimate" to 1L, "createdAt" to 5L, "updatedAt" to 6L, "deleted" to 0L,
        )
        val json = SyncTables.logEntry.toRemote(row)
        assertEquals(JsonPrimitive(true), json["is_estimate"])
        assertEquals(JsonPrimitive(false), json["deleted"])
        assertEquals(JsonPrimitive(250.0), json["portion_grams"])
        assertEquals(JsonNull, json["portion_label"])
        val back = SyncTables.logEntry.toLocal(json)
        assertEquals(true, back["isEstimate"])
        assertEquals(250.0, back["portionGrams"])
    }

    @Test
    fun `every table has id, updatedAt, deleted and its owner column`() {
        for (table in SyncTables.all) {
            val names = table.columns.map { it.local }
            assertTrue(table.local, names.containsAll(listOf("id", "updatedAt", "deleted", table.ownerColumn)))
            assertEquals(table.local, names.size, names.toSet().size)
        }
    }

    @Test
    fun `NEVO foods never sync`() {
        assertEquals("source != 'NEVO'", SyncTables.food.localFilter)
    }

    @Test
    fun `recipes are pushed before their variants`() {
        assertTrue(SyncTables.all.indexOf(SyncTables.recipe) < SyncTables.all.indexOf(SyncTables.recipeVariant))
    }
}
