package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.PickedFood

/**
 * What the camera handed back, as an ingredient: a scanned product (its food, and the grams chosen
 * there, 0 when none). Null when the camera only weighed (no food), so the grams fill the line being weighed.
 */
internal fun scannedIngredient(foodId: String?, grams: Double?): PickedFood? {
    if (foodId.isNullOrBlank()) return null
    return PickedFood(foodId, grams?.takeIf { it > 0 } ?: 0.0)
}
