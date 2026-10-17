package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchContainer
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.StoragePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SharedPlatesTest {
    // 1000 kcal of raw ingredients, 1000 g dished out in all: 1 kcal per gram.
    private val chili = Batch(
        id = "chili", householdId = "h", recipeId = null, name = "Chili",
        ingredients = listOf(Ingredient("beans", "Beans", 1000.0, Nutrients(100.0, 5.0, 10.0, 2.0))),
        cookedWeightG = 1000.0, cookedOn = TODAY,
    )
    private val plate = BatchPortion("p1", "chili", "h", userId = "partner", grams = 380.0, servedBy = "guido")

    @Test
    fun waitingPlatesCarryTheirBatchAndWhoDishedUp() {
        val plates = waitingPlates(
            waiting = listOf(plate, plate.copy(id = "p2", batchId = "not-synced-yet"), plate.copy(id = "p3", logEntryId = "e3")),
            batches = listOf(chili),
            members = listOf(HouseholdMember("guido", "Guido"), HouseholdMember("partner", "Sam")),
        )
        assertEquals(listOf("p1"), plates.map { it.portion.id })
        assertEquals("Guido", plates.single().servedByName)
        assertEquals(380.0, plates.single().kcal, 0.001)
    }

    @Test
    fun aBlankNameIsUnknown() {
        val plates = waitingPlates(listOf(plate), listOf(chili), listOf(HouseholdMember("guido", " ")))
        assertNull(plates.single().servedByName)
        assertNull(waitingPlates(listOf(plate), listOf(chili), emptyList()).single().servedByName)
    }

    @Test
    fun fridgeCountSkipsTheFreezerEmptyAndDeletedBoxes() {
        val boxes = listOf(
            BatchContainer("c1", "chili", "h", 450.0, StoragePlace.FRIDGE, 1),
            BatchContainer("c2", "chili", "h", 300.0, StoragePlace.FREEZER, 2),
            BatchContainer("c3", "chili", "h", 200.0, StoragePlace.FRIDGE, 3),
            BatchContainer("c4", "chili", "h", 200.0, StoragePlace.FRIDGE, 4, deleted = true),
            BatchContainer("c5", "gone", "h", 200.0, StoragePlace.FRIDGE, 1),
        )
        val portions = listOf(BatchPortion("p9", "chili", "h", "guido", 199.5, logEntryId = "e9", containerId = "c3"))
        assertEquals(1, boxesInFridge(boxes, listOf(chili), portions))
        assertEquals(2, boxesInFridge(boxes, listOf(chili), emptyList()))
    }

    @Test
    fun addingLogsTheSharePerGram() {
        val waiting = WaitingPlate(plate, chili, "Guido")
        val entry = plateEntry(waiting, "partner", TODAY, Meal.DINNER, Instant.EPOCH)
        assertEquals("partner", entry.userId)
        assertEquals(Meal.DINNER, entry.meal)
        assertEquals(Logged.BatchShare("chili", "Chili"), entry.what)
        assertEquals(380.0, entry.portion.grams, 0.0)
        assertEquals(380.0, entry.nutrients.kcal, 0.001)
    }

    @Test
    fun notMineBecomesTheNextFridgeBox() {
        val waiting = WaitingPlate(plate, chili, "Guido")
        val existing = listOf(
            BatchContainer("c1", "chili", "h", 450.0, StoragePlace.FRIDGE, 1),
            BatchContainer("c2", "chili", "h", 300.0, StoragePlace.FREEZER, 2),
            BatchContainer("x", "other", "h", 300.0, StoragePlace.FRIDGE, 7),
        )
        val box = notMineBox(waiting, existing)
        assertEquals(3, box.number)
        assertEquals(380.0, box.grams, 0.0)
        assertEquals(StoragePlace.FRIDGE, box.place)
        assertEquals(1, notMineBox(waiting, emptyList()).number)
    }
}
