package nl.guido.foodtracker.feature.food.pick

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.feature.food.pieces.Piece
import kotlin.math.abs

/**
 * Foods recently put in a recipe or batch (S14), newest first and each once: ingredients of the newest
 * batches first, then those of the household's recipes.
 */
internal fun recentIngredientIds(batches: List<Batch>, recipes: List<Recipe>, limit: Int = 15): List<Id> =
    (batches.sortedByDescending { it.cookedOn }.flatMap { it.ingredients } + recipes.flatMap { it.ingredients })
        .map { it.foodId }
        .distinct()
        .take(limit)

/** Search results with the recently used ingredients first, each group keeping the search order. */
internal fun recentFirst(found: List<Food>, recent: List<Id>): List<Food> {
    val (used, others) = found.partition { it.id in recent }
    return used + others
}

/** A ready-made amount in the picker (S13): a number of pieces ("2 eggs"), one serving or the whole pack. */
internal data class PickChip(val kind: Kind, val grams: Double, val count: Int = 1) {
    enum class Kind { PIECE, SERVING, PACK }
}

/**
 * Chips for a food, like Log food's amount sheet: 1, 2 and 3 pieces when the list can count them
 * ("1 egg", "2 eggs", "3 eggs"; else just one piece), then one serving and the whole pack from the food's sizes.
 */
internal fun pickChips(food: Food, piece: Piece?, language: String): List<PickChip> {
    val pieces = when {
        piece == null || piece.grams <= 0 -> emptyList()
        piece.countable(language) -> (1..3).map { PickChip(PickChip.Kind.PIECE, piece.grams * it, it) }
        else -> listOf(PickChip(PickChip.Kind.PIECE, piece.grams))
    }
    val sizes = listOfNotNull(
        food.servingG?.takeIf { it > 0 }?.let { PickChip(PickChip.Kind.SERVING, it) },
        food.packageG?.takeIf { it > 0 }?.let { PickChip(PickChip.Kind.PACK, it) },
    )
    // A pack that is one serving (a single yoghurt pot) needs only one chip.
    return pieces + sizes.filter { size -> pieces.none { it.grams == size.grams } }.distinctBy { it.grams }
}

/**
 * What the picker hands back: the food, the grams and how the amount was chosen. The chip's label
 * ("2 eggs") stays only while the grams are still the chip's; typed grams have no label.
 */
internal fun pickedFood(food: Food, grams: Double, chip: PickChip?, chipLabel: String?): PickedFood =
    PickedFood(food.id, grams, chipLabel?.takeIf { chip != null && abs(chip.grams - grams) < 0.5 })

/** Grams typed in the amount field ("150", "12,5"); null when it isn't a positive number. */
internal fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 100_000 }
