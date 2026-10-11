package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.newId
import kotlin.math.roundToInt

/** An ingredient line being edited on screen: the grams are still text until saved. */
data class EditableIngredient(
    val foodId: Id,
    val name: String,
    val per100g: Nutrients,
    val gramsText: String = "",
    /** How the amount was chosen, e.g. "2 eggs" or "1 serving" (S13). Null = grams typed or weighed. */
    val amountLabel: String? = null,
    /** Liquids are measured in ml (1 ml counted as 1 g, as on Log food). */
    val isDrink: Boolean = false,
    /** Ready-made amounts for this food: pieces, a serving, the pack (S13). */
    val choices: List<Portion> = emptyList(),
    /** Identifies this line on screen, e.g. to know which line a scale reading belongs to. */
    val key: String = newId(),
) {
    val grams: Double? get() = parseGrams(gramsText)

    fun toIngredient(): Ingredient? = grams?.let { Ingredient(foodId, name, it, per100g, amountLabel) }

    /** Typed or weighed grams: the label no longer fits, so it goes. */
    fun withGramsText(text: String): EditableIngredient =
        if (text == gramsText) this else copy(gramsText = text, amountLabel = null)

    /** A ready-made amount such as "1 egg": grams and label together. */
    fun withChoice(choice: Portion): EditableIngredient =
        copy(gramsText = formatGrams(choice.grams), amountLabel = choice.label)

    /** Adds what is known about the food (liquid, ready-made amounts) to a line loaded from a saved recipe. */
    fun withFood(food: Food?, choices: List<Portion>): EditableIngredient =
        if (food == null) this else copy(isDrink = food.isDrink, choices = choices)

    companion object {
        fun of(food: Food, choices: List<Portion> = emptyList()) =
            EditableIngredient(food.id, food.name, food.per100g, isDrink = food.isDrink, choices = choices)
        fun of(ingredient: Ingredient) = EditableIngredient(
            ingredient.foodId, ingredient.name, ingredient.per100g, formatGrams(ingredient.grams), ingredient.amountLabel,
        )
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

/**
 * Ready-made amounts for a food, as on Log food: its pieces ("1 egg"), one serving, the whole pack.
 * [servingLabel] and [packLabel] are the words in the phone's language.
 */
fun portionChoices(food: Food, pieces: List<Portion>, servingLabel: String, packLabel: String): List<Portion> =
    (pieces + listOfNotNull(
        food.servingG?.takeIf { it > 0.0 }?.let { Portion(it, servingLabel) },
        food.packageG?.takeIf { it > 0.0 }?.let { Portion(it, packLabel) },
    )).distinctBy { it.grams to it.label }
