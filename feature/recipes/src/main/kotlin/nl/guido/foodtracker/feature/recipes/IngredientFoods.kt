package nl.guido.foodtracker.feature.recipes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.CommonPortions
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
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

    /** A new line for a food picked just now. */
    suspend fun line(food: Food): EditableIngredient = EditableIngredient.of(food, choicesFor(food))

    /** Lines loaded from a saved recipe or batch, with their foods looked up on the phone. */
    suspend fun enrich(lines: List<EditableIngredient>): List<EditableIngredient> = lines.map { line ->
        val food = runCatching { foods.get(line.foodId) }.getOrNull()
        line.withFood(food, food?.let { choicesFor(it) }.orEmpty())
    }
}
