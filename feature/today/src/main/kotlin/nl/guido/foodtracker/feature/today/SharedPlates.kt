package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchContainer
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.StoragePlace
import nl.guido.foodtracker.core.model.gramsLeft
import nl.guido.foodtracker.core.model.newId
import java.time.Instant
import java.time.LocalDate

/*
 * Cooking for two or more on Today (round 6, D2 + D3). Same rules as feature/recipes'
 * WaitingPlatesViewModel and storedBoxes, which own the dish-up and the fridge screen.
 */

/** A plate someone dished up for me, with the batch it came from; [servedByName] null when their name isn't known. */
internal data class WaitingPlate(val portion: BatchPortion, val batch: Batch, val servedByName: String?) {
    val kcal: Double get() = batch.shareFor(portion.grams).kcal
}

/** My waiting plates with their batch and the name of who dished up. Plates of a batch not synced yet wait out of sight. */
internal fun waitingPlates(waiting: List<BatchPortion>, batches: List<Batch>, members: List<HouseholdMember>): List<WaitingPlate> {
    val batchById = batches.associateBy { it.id }
    val names = members.associate { it.userId to it.displayName.takeIf { name -> name.isNotBlank() } }
    return waiting.filter { it.isWaiting }
        .mapNotNull { p -> batchById[p.batchId]?.let { WaitingPlate(p, it, names[p.servedBy]) } }
}

/** Boxes in the fridge (not the freezer) with at least [MIN_BOX_LEFT_G] left: the number on the fridge button. */
internal fun boxesInFridge(containers: List<BatchContainer>, batches: List<Batch>, portions: List<BatchPortion>): Int {
    val batchIds = batches.mapTo(HashSet()) { it.id }
    return containers.count {
        !it.deleted && it.place == StoragePlace.FRIDGE && it.batchId in batchIds && it.gramsLeft(portions) >= MIN_BOX_LEFT_G
    }
}

/** Less than this in a box counts as empty, as on the fridge screen. */
internal const val MIN_BOX_LEFT_G = 1.0

/** "Add to my diary": the plate as my diary line for [date] and [meal]. */
internal fun plateEntry(plate: WaitingPlate, userId: Id, date: LocalDate, meal: Meal, now: Instant): LogEntry = LogEntry(
    id = newId(),
    userId = userId,
    date = date,
    meal = meal,
    what = Logged.BatchShare(batchId = plate.batch.id, name = plate.batch.name),
    portion = Portion(plate.portion.grams),
    nutrients = plate.batch.shareFor(plate.portion.grams),
    isEstimate = false,
    createdAt = now,
)

/** "Not mine": the plate's grams become one more fridge box of its batch, so nothing is lost. */
internal fun notMineBox(plate: WaitingPlate, containers: List<BatchContainer>): BatchContainer = BatchContainer(
    id = newId(),
    batchId = plate.batch.id,
    householdId = plate.batch.householdId,
    grams = plate.portion.grams,
    place = StoragePlace.FRIDGE,
    number = (containers.filter { it.batchId == plate.batch.id }.maxOfOrNull { it.number } ?: 0) + 1,
)
