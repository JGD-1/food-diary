package nl.guido.foodtracker.feature.camera

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.camera.read.LabelValues

/** The food form after a label scan, typed in by hand, or opened to edit a food: text fields. */
data class FoodDraft(
    val name: String = "",
    val brand: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val isDrink: Boolean = false,
    val barcode: String? = null,
    // Optional; left empty means "not known" (not 0).
    val fibre: String = "",
    val sugar: String = "",
    val salt: String = "",
) {
    /** Name and kcal are needed; protein, carbs and fat count as 0 when left empty; the rest may stay empty. */
    val canSave: Boolean
        get() = name.isNotBlank() && parseAmount(kcal) != null &&
            listOf(protein, carbs, fat, fibre, sugar, salt).all { it.isBlank() || parseAmount(it) != null }

    private fun nutrients() = Nutrients(
        kcal = parseAmount(kcal)!!,
        protein = parseAmount(protein) ?: 0.0,
        carbs = parseAmount(carbs) ?: 0.0,
        fat = parseAmount(fat) ?: 0.0,
        fibre = parseAmount(fibre),
        sugar = parseAmount(sugar),
        salt = parseAmount(salt),
    )

    fun toFood(id: Id, ownerId: Id): Food? {
        if (!canSave) return null
        return Food(
            id = id,
            name = name.trim(),
            brand = brand.trim().ifEmpty { null },
            barcode = barcode,
            per100g = nutrients(),
            source = FoodOrigin.LABEL,
            isDrink = isDrink,
            ownerId = ownerId,
        )
    }

    /**
     * The edited food: same id, source, owner, barcode and pack sizes, new name, brand and per 100 g values.
     * Diary lines already logged keep their own numbers (they were copied in when logged).
     */
    fun applyTo(original: Food): Food? {
        if (!canSave) return null
        return original.copy(
            name = name.trim(),
            brand = brand.trim().ifEmpty { null },
            per100g = nutrients(),
            isDrink = isDrink,
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
            fibre = values.fibre.asField(),
            sugar = values.sugar.asField(),
            salt = values.salt.asField(),
        )

        /** The form filled in with a food's current values, for "Edit food". */
        fun fromFood(food: Food) = FoodDraft(
            name = food.name,
            brand = food.brand.orEmpty(),
            kcal = food.per100g.kcal.asField(),
            protein = food.per100g.protein.asField(),
            carbs = food.per100g.carbs.asField(),
            fat = food.per100g.fat.asField(),
            isDrink = food.isDrink,
            barcode = food.barcode,
            fibre = food.per100g.fibre.asField(),
            sugar = food.per100g.sugar.asField(),
            salt = food.per100g.salt.asField(),
        )

        /** Whole numbers without ".0"; others rounded to at most 2 decimals, so "8.1" not "8.099999". */
        private fun Double?.asField(): String = when {
            this == null -> ""
            this == Math.floor(this) -> toLong().toString()
            else -> java.math.BigDecimal(this).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }
}

/** NEVO foods stay unchanged (licence); everything else (own, label-scanned, Open Food Facts) can be edited. */
fun Food.canEdit(): Boolean = source != FoodOrigin.NEVO

/** Reads a typed amount; accepts a Dutch decimal comma ("12,5"). Null when it is not a number of 0 or more. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
