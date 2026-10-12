package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Pan
import java.time.LocalDate
import kotlin.math.roundToInt

/** "× ½", "× 1", "× 2": how much of the recipe goes into this batch. */
val BATCH_SCALES: List<Double> = listOf(0.5, 1.0, 2.0)

/**
 * The lines when going from [from] times the recipe to [to] times it. Grams typed by hand are
 * scaled along, so a change made before picking × 2 is kept; lines without grams stay empty.
 */
fun List<EditableIngredient>.rescaled(from: Double, to: Double): List<EditableIngredient> {
    if (from <= 0.0 || to <= 0.0 || from == to) return this
    // "2 eggs" no longer fits once doubled, so the label goes and the grams stay.
    return map { line -> line.grams?.let { line.copy(gramsText = formatGrams(it * to / from), amountLabel = null) } ?: line }
}

/** True when the batch's ingredients differ from the recipe's (a food added, removed, or other grams). */
fun amountsChanged(recipe: List<Ingredient>, batch: List<Ingredient>): Boolean {
    if (recipe.size != batch.size) return true
    return recipe.zip(batch).any { (r, b) -> r.foodId != b.foodId || formatGrams(r.grams) != formatGrams(b.grams) }
}

/** A cook date can be any day up to [today], never later. */
fun cookDateAllowed(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today)

/** The cooked pot from a scale reading (S9): minus the empty pan when one is picked. Null when not positive. */
fun cookedFromReading(reading: Double?, pan: Pan?): Double? {
    val net = if (pan == null) reading else reading?.minus(pan.grams)
    return net?.takeIf { it > 0.0 }
}

/** "Makes 12 pieces" × ½ is 6 pieces; never fewer than 1. */
fun scaledPieces(pieces: Int, from: Double, to: Double): Int =
    if (from <= 0.0 || to <= 0.0) pieces else maxOf(1, (pieces * to / from).roundToInt())

/** About how many pieces are left in the pot, or null when the batch isn't made in pieces. */
fun Batch.piecesLeft(gramsLeft: Double): Double? {
    val one = piecesGrams(1.0) ?: return null
    return (gramsLeft / one).coerceAtLeast(0.0)
}

/** Batches on the list split into the pot and the freezer (S15). */
data class BatchLists(val inPot: List<OpenBatch>, val frozen: List<OpenBatch>)

fun List<OpenBatch>.byFreezer(): BatchLists = BatchLists(filterNot { it.batch.frozen }, filter { it.batch.frozen })

/**
 * My portion as a share of the whole cooked batch, in whole percent ("23% of the pot"); never
 * shown as 0% for a small spoonful. Null when the batch has no cooked weight.
 */
fun shareOfPot(portionGrams: Double, cookedWeightG: Double): Int? {
    if (cookedWeightG <= 0.0 || portionGrams <= 0.0) return null
    return maxOf(1, (portionGrams / cookedWeightG * 100).roundToInt())
}
