package nl.guido.foodtracker.feature.camera

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.ui.amountText

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

/**
 * The chip's text in the one amount format (S27): "1 serving ≈ 45 g", "Whole pack ≈ 750 g",
 * "1 apple ≈ 150 g"; ml for drinks. [serving] and [pack] are the words for those chips.
 */
internal fun chipText(chip: AmountChip, serving: String, pack: String, drink: Boolean): String {
    val label = when (chip.kind) {
        AmountChip.Kind.SERVING -> serving
        AmountChip.Kind.PACK -> pack
        AmountChip.Kind.PIECE -> chip.label
    }
    return amountText(label, chip.grams, drink)
}
