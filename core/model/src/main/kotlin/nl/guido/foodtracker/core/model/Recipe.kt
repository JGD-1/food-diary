package nl.guido.foodtracker.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** A food in a recipe or batch. The values per 100 g are copied in so totals never need a lookup. */
@Serializable
data class Ingredient(
    val foodId: Id,
    val name: String,
    val grams: Double,
    val per100g: Nutrients,
) {
    val nutrients: Nutrients get() = per100g.forGrams(grams)
}

/** Shared within the household. */
data class Recipe(
    val id: Id,
    val householdId: Id,
    val name: String,
    val ingredients: List<Ingredient>,
    val pinned: Boolean = false,
    val usualPortion: Portion? = null,
) {
    val totalGrams: Double get() = ingredients.sumOf { it.grams }
    val total: Nutrients get() = ingredients.map { it.nutrients }.sum()
}

/** A saved change to a recipe, e.g. "with extra cheese". */
data class RecipeVariant(
    val id: Id,
    val baseRecipeId: Id,
    val name: String,
    val extras: List<Ingredient>,
)

/**
 * A pot of food cooked once and eaten in portions by several people.
 * Each person weighs their portion; they log that share of the whole pot.
 */
data class Batch(
    val id: Id,
    val householdId: Id,
    val recipeId: Id?,
    val name: String,
    val ingredients: List<Ingredient>,
    val cookedWeightG: Double,
    val cookedOn: LocalDate,
    /** Set when someone taps "Finished"; the batch then leaves the list. Null = still in the pot. */
    val finishedOn: LocalDate? = null,
) {
    val total: Nutrients get() = ingredients.map { it.nutrients }.sum()

    /** Nutrients in [portionGrams] of the cooked batch. */
    fun shareFor(portionGrams: Double): Nutrients =
        if (cookedWeightG <= 0.0) Nutrients.ZERO else total * (portionGrams / cookedWeightG)
}

/**
 * How many grams one person took from a batch. Shared within the household (no kcal, no diary),
 * so everyone sees how much is left in the pot. [logEntryId] is the diary line it came with.
 */
data class BatchPortion(
    val id: Id,
    val batchId: Id,
    val householdId: Id,
    val userId: Id,
    val grams: Double,
    val logEntryId: Id? = null,
)

/** Grams left in the pot after these portions (never below 0). */
fun Batch.gramsLeft(portions: List<BatchPortion>): Double =
    (cookedWeightG - portions.filter { it.batchId == id }.sumOf { it.grams }).coerceAtLeast(0.0)

/**
 * One person's pinned recipe or food with their own usual portion. Private to that person.
 * Exactly one of [recipeId] and [foodId] is set.
 */
data class Favourite(
    val id: Id,
    val userId: Id,
    val recipeId: Id? = null,
    val usualPortion: Portion? = null,
    val foodId: Id? = null,
)
