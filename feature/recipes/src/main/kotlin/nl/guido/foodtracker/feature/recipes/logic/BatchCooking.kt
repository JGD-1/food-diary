package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Ingredient
import java.time.LocalDate

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
