package nl.guido.foodtracker.feature.recipes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.CommonPortions
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.portionChoices
import java.util.Optional
import javax.inject.Inject

/** What an ingredient line needs to know about its food: liquid or not, and its ready-made amounts (S13). */
internal class IngredientFoods @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foods: FoodRepository,
    private val commonPortions: Optional<CommonPortions>,
) {
    suspend fun choicesFor(food: Food): List<Portion> = portionChoices(
        food,
        pieces = if (commonPortions.isPresent) runCatching { commonPortions.get().forFood(food) }.getOrDefault(emptyList()) else emptyList(),
        servingLabel = context.getString(R.string.recipes_amount_serving),
        packLabel = context.getString(R.string.recipes_amount_pack),
    )

    /** Grams in one piece of [food] ("1 egg ≈ 50 g"), or null when it isn't eaten in pieces. */
    suspend fun pieceGrams(food: Food): Double? =
        if (commonPortions.isPresent) runCatching { commonPortions.get().forFood(food) }.getOrNull()?.firstOrNull()?.grams else null

    /** A line for a food read from a note (round 4, H); the grams stay empty when the note had no amount. */
    suspend fun line(foodId: Id, grams: Double?, label: String?): EditableIngredient? {
        val food = runCatching { foods.get(foodId) }.getOrNull() ?: return null
        return EditableIngredient.of(food, choicesFor(food))
            .copy(gramsText = grams?.let { formatGrams(it) }.orEmpty(), amountLabel = label?.takeIf { grams != null })
    }

    /** A new line for a food picked in the shared picker (S21), with its amount and label ("2 eggs"). Null if the food is gone. */
    suspend fun line(picked: PickedFood): EditableIngredient? {
        val food = runCatching { foods.get(picked.foodId) }.getOrNull() ?: return null
        return EditableIngredient.of(food, choicesFor(food))
            .copy(gramsText = formatGrams(picked.grams), amountLabel = picked.label)
    }

    /** Lines loaded from a saved recipe or batch, with their foods looked up on the phone. */
    suspend fun enrich(lines: List<EditableIngredient>): List<EditableIngredient> = lines.map { line ->
        val food = runCatching { foods.get(line.foodId) }.getOrNull()
        line.withFood(food, food?.let { choicesFor(it) }.orEmpty())
    }
}
