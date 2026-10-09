package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.newId
import kotlin.math.roundToInt

/** An ingredient line being edited on screen: the grams are still text until saved. */
data class EditableIngredient(
    val foodId: Id,
    val name: String,
    val per100g: Nutrients,
    val gramsText: String = "",
    /** Identifies this line on screen, e.g. to know which line a scale reading belongs to. */
    val key: String = newId(),
) {
    val grams: Double? get() = parseGrams(gramsText)

    fun toIngredient(): Ingredient? = grams?.let { Ingredient(foodId, name, it, per100g) }

    companion object {
        fun of(food: Food) = EditableIngredient(food.id, food.name, food.per100g)
        fun of(ingredient: Ingredient) =
            EditableIngredient(ingredient.foodId, ingredient.name, ingredient.per100g, formatGrams(ingredient.grams))
    }
}

/** All lines as ingredients, or null while any line has no valid grams yet. */
fun List<EditableIngredient>.toIngredients(): List<Ingredient>? {
    val result = map { it.toIngredient() ?: return null }
    return result
}

/** Lines that already have grams, for showing running totals while editing. */
fun List<EditableIngredient>.filledIn(): List<Ingredient> = mapNotNull { it.toIngredient() }

/** Grams for a text field: whole numbers without ".0", otherwise one decimal. */
fun formatGrams(grams: Double): String {
    val rounded = (grams * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
