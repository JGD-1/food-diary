package nl.guido.foodtracker.feature.sync

import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.engine.toLocal
import nl.guido.foodtracker.feature.sync.engine.toRemote
import nl.guido.foodtracker.feature.sync.share.dayTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DayShareTest {
    private val mon = LocalDate.of(2026, 11, 2)

    private fun entry(id: String, day: LocalDate, kcal: Double, user: String = "me") = LogEntry(
        id = id, userId = user, date = day, meal = Meal.LUNCH, what = Logged.FoodRef("f", "Bread"),
        portion = Portion(100.0), nutrients = Nutrients(kcal, 0.0, 0.0, 0.0), isEstimate = false,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `every day gets a total, empty days zero`() {
        val totals = dayTotals(
            "me", listOf(entry("a", mon, 300.4), entry("b", mon, 200.2), entry("c", mon.plusDays(2), 99.6)),
            2000, mon, mon.plusDays(3),
        )
        assertEquals(listOf(501, 0, 100, 0), totals.map { it.kcal })
        assertEquals((0..3).map { mon.plusDays(it.toLong()) }, totals.map { it.date })
        assertTrue(totals.all { it.targetKcal == 2000 && it.userId == "me" })
    }

    @Test
    fun `sport raises that day's shared target only`() {
        val totals = dayTotals("me", emptyList(), 2000, mon, mon.plusDays(1), mapOf(mon to 300))
        assertEquals(listOf(2300, 2000), totals.map { it.targetKcal })
        assertEquals(null, dayTotals("me", emptyList(), null, mon, mon, mapOf(mon to 300)).single().targetKcal)
    }

    @Test
    fun `someone else's entries never count`() {
        val totals = dayTotals("me", listOf(entry("a", mon, 300.0, user = "partner")), null, mon, mon)
        assertEquals(0, totals.single().kcal)
        assertEquals(null, totals.single().targetKcal)
    }

    @Test
    fun `day totals go online with only the kcal and target`() {
        val row = mapOf(
            "id" to "d1", "userId" to "me", "date" to "2026-11-06", "kcal" to 1240L, "targetKcal" to null,
            "updatedAt" to 9L, "deleted" to 0L,
        )
        val json = SyncTables.dayTotal.toRemote(row)
        assertEquals(setOf("id", "user_id", "date", "kcal", "target_kcal", "updated_at", "deleted"), json.keys)
        assertEquals(1240L, SyncTables.dayTotal.toLocal(json)["kcal"])
        assertTrue(SyncTables.dayTotal in SyncTables.all)
        assertTrue(SyncTables.dayTotal in SyncTables.refetchOnHouseholdChange)
    }
}
