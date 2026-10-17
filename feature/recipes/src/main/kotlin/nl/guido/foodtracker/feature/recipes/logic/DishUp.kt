package nl.guido.foodtracker.feature.recipes.logic

import kotlinx.serialization.Serializable
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchContainer
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.StoragePlace
import nl.guido.foodtracker.core.model.gramsLeft
import nl.guido.foodtracker.core.model.newId
import kotlin.math.roundToInt

/**
 * Dish up (round 6, D1): instead of weighing the hot pot, every plate and box is weighed as it is
 * filled (the scale zeroed with the empty plate on it). Everything dished out together is the
 * whole cooked batch, so each plate or box gets its share of the raw ingredients by weight.
 */
@Serializable
data class DishRow(
    val key: String = newId(),
    val kind: DishKind,
    /** Whose plate, for [DishKind.PLATE]: null = mine. */
    val forUserId: Id? = null,
    val gramsText: String = "",
) {
    val grams: Double? get() = parseGrams(gramsText)
}

@Serializable
enum class DishKind {
    PLATE,
    /** Someone else eats it, not logged in anyone's diary. */
    GUEST,
    FRIDGE,
    FREEZER,
}

/** The rows to start with: my plate, then a plate for everyone else in the household. */
fun startingRows(myUserId: Id, members: List<HouseholdMember>): List<DishRow> =
    listOf(DishRow(kind = DishKind.PLATE)) +
        members.filter { it.userId != myUserId }.map { DishRow(kind = DishKind.PLATE, forUserId = it.userId) }

/** Everything dished out so far; with [restText] (a guess of what is left in the pot) counted too. */
fun dishedTotal(rows: List<DishRow>, restText: String = ""): Double =
    rows.mapNotNull { it.grams }.sum() + (parseGrams(restText) ?: 0.0)

/** The nutrients in [grams] of a dish whose raw ingredients add up to [raw] and which weighs [total]. */
fun shareOf(raw: Nutrients, grams: Double, total: Double): Nutrients =
    if (total <= 0.0) Nutrients.ZERO else raw * (grams / total)

/** Whole percent of [total], at least 1 for anything weighed; null when there is nothing to share. */
fun percentOf(grams: Double, total: Double): Int? {
    if (total <= 0.0 || grams <= 0.0) return null
    return maxOf(1, (grams / total * 100).roundToInt())
}

/** What "That's everything" saves, apart from the batch itself. */
data class DishOutcome(
    /** My plate, to log in my diary (null when I didn't dish up for myself). */
    val myGrams: Double?,
    /** Plates waiting for others, and guest plates (counted, logged nowhere). */
    val portions: List<BatchPortion>,
    val containers: List<BatchContainer>,
)

/**
 * The plates and boxes of [batch] dished up by [me]. Other people's plates wait for them
 * ([BatchPortion.isWaiting]); a guest's plate is taken but in nobody's diary; boxes go to the
 * fridge or freezer, numbered in order. A guess of the rest in the pot becomes one more fridge box.
 * More than one plate of mine adds up into one diary line.
 */
fun dishOutcome(batch: Batch, me: Id, rows: List<DishRow>, restText: String = ""): DishOutcome {
    val weighed = rows.filter { it.grams != null }
    val mine = weighed.filter { it.kind == DishKind.PLATE && (it.forUserId == null || it.forUserId == me) }
        .sumOf { it.grams ?: 0.0 }.takeIf { it > 0.0 }
    val portions = weighed.mapNotNull { row ->
        val grams = row.grams ?: return@mapNotNull null
        when {
            row.kind == DishKind.PLATE && row.forUserId != null && row.forUserId != me ->
                BatchPortion(newId(), batch.id, batch.householdId, userId = row.forUserId, grams = grams, servedBy = me)
            row.kind == DishKind.GUEST ->
                BatchPortion(newId(), batch.id, batch.householdId, userId = me, grams = grams)
            else -> null
        }
    }
    val boxes = weighed.filter { it.kind == DishKind.FRIDGE || it.kind == DishKind.FREEZER }
        .map { (if (it.kind == DishKind.FREEZER) StoragePlace.FREEZER else StoragePlace.FRIDGE) to (it.grams ?: 0.0) } +
        listOfNotNull(parseGrams(restText)?.let { StoragePlace.FRIDGE to it })
    val containers = boxes.mapIndexed { i, (place, grams) ->
        BatchContainer(newId(), batch.id, batch.householdId, grams = grams, place = place, number = i + 1)
    }
    return DishOutcome(mine, portions, containers)
}

/** A box on the fridge list: what is left in it, with the batch it came from. */
data class StoredBox(val container: BatchContainer, val batch: Batch, val gramsLeft: Double)

/** Boxes with something left in them, newest cook first, then by number. Empty or deleted boxes leave the list. */
fun storedBoxes(containers: List<BatchContainer>, batches: List<Batch>, portions: List<BatchPortion>): List<StoredBox> {
    val batchById = batches.associateBy { it.id }
    return containers.filter { !it.deleted }
        .mapNotNull { box -> batchById[box.batchId]?.let { StoredBox(box, it, box.gramsLeft(portions)) } }
        .filter { it.gramsLeft >= MIN_LEFT_G }
        .sortedWith(compareByDescending<StoredBox> { it.batch.cookedOn }.thenBy { it.container.number })
}

/** Less than this in a box counts as empty (a scraping is not worth a line on the list). */
const val MIN_LEFT_G = 1.0
