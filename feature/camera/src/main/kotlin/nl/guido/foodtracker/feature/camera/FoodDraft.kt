package nl.guido.foodtracker.feature.camera

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.camera.read.LabelValues

/** The "new food" form after a label scan (or typed in by hand), as the user sees it: text fields. */
data class FoodDraft(
    val name: String = "",
    val brand: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val isDrink: Boolean = false,
    val barcode: String? = null,
) {
    /** Name and kcal are needed; protein, carbs and fat count as 0 when left empty. */
    val canSave: Boolean
        get() = name.isNotBlank() && parseAmount(kcal) != null &&
            listOf(protein, carbs, fat).all { it.isBlank() || parseAmount(it) != null }

    fun toFood(id: Id, ownerId: Id): Food? {
        if (!canSave) return null
        return Food(
            id = id,
            name = name.trim(),
            brand = brand.trim().ifEmpty { null },
            barcode = barcode,
            per100g = Nutrients(
                kcal = parseAmount(kcal)!!,
                protein = parseAmount(protein) ?: 0.0,
                carbs = parseAmount(carbs) ?: 0.0,
                fat = parseAmount(fat) ?: 0.0,
            ),
            source = FoodOrigin.LABEL,
            isDrink = isDrink,
            ownerId = ownerId,
        )
    }

    companion object {
        fun fromLabel(values: LabelValues, barcode: String?, drinkHint: Boolean) = FoodDraft(
            kcal = values.kcal.asField(),
            protein = values.protein.asField(),
            carbs = values.carbs.asField(),
            fat = values.fat.asField(),
            isDrink = drinkHint || values.perMl,
            barcode = barcode,
        )

        private fun Double?.asField(): String = when {
            this == null -> ""
            this == Math.floor(this) -> toLong().toString()
            else -> toString()
        }
    }
}

/** Reads a typed amount; accepts a Dutch decimal comma ("12,5"). Null when it is not a number of 0 or more. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
