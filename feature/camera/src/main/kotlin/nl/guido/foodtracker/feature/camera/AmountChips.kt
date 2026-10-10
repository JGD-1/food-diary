package nl.guido.foodtracker.feature.camera

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Portion

/** A quick amount on "How much?": one serving, the whole pack, or a common piece ("1 apple"). */
internal data class AmountChip(val kind: Kind, val grams: Double, val label: String? = null) {
    enum class Kind { SERVING, PACK, PIECE }
}

/**
 * The chips for a food: its serving and pack sizes (from Open Food Facts), then common pieces
 * ([pieces], e.g. "1 apple ≈ 150 g"). A pack the same size as a serving is shown once.
 */
internal fun amountChips(food: Food, pieces: List<Portion> = emptyList()): List<AmountChip> {
    val chips = mutableListOf<AmountChip>()
    food.servingG?.takeIf { it > 0 }?.let { chips += AmountChip(AmountChip.Kind.SERVING, it) }
    food.packageG?.takeIf { it > 0 && it != food.servingG }?.let { chips += AmountChip(AmountChip.Kind.PACK, it) }
    pieces.filter { it.grams > 0 }.forEach { chips += AmountChip(AmountChip.Kind.PIECE, it.grams, it.label) }
    return chips.distinctBy { it.kind to it.grams }
}
