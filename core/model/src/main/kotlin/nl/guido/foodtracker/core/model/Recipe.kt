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
    /** How the amount was entered, e.g. "2 eggs" or "1 serving" (S13). Null = typed grams. */
    val amountLabel: String? = null,
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
    /** "Makes … pieces" (S11), e.g. 12 muffins. Null = not made in pieces. */
    val pieces: Int? = null,
    /** Weight of the whole dish after cooking or baking (S12). Null = portions are raw ingredient grams. */
    val cookedWeightG: Double? = null,
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
    /** The whole cooked dish. From round 6 the sum of every plate and container at "That's everything". */
    val cookedWeightG: Double,
    val cookedOn: LocalDate,
    /** Set when someone taps "Finished"; the batch then leaves the list. Null = still in the pot. */
    val finishedOn: LocalDate? = null,
    /** "Makes … pieces" (S11). Null = not made in pieces. */
    val pieces: Int? = null,
    /** In the freezer (S15): shown in its own short list. Kept for older batches; round 6 uses [BatchContainer.place]. */
    val frozen: Boolean = false,
) {
    val total: Nutrients get() = ingredients.map { it.nutrients }.sum()

    /** Nutrients in [portionGrams] of the cooked batch. */
    fun shareFor(portionGrams: Double): Nutrients =
        if (cookedWeightG <= 0.0) Nutrients.ZERO else total * (portionGrams / cookedWeightG)
}

/**
 * How many grams one person took from a batch. Shared within the household (no kcal, no diary),
 * so everyone sees how much is left in the pot. [logEntryId] is the diary line it came with.
 * A plate dished up for someone else waits for them until they add it: see [isWaiting].
 */
data class BatchPortion(
    val id: Id,
    val batchId: Id,
    val householdId: Id,
    /** Whose plate this is. */
    val userId: Id,
    val grams: Double,
    val logEntryId: Id? = null,
    /** Who dished it up (round 6). Null = took it myself. */
    val servedBy: Id? = null,
    /** Taken from this fridge/freezer container ([BatchContainer.id]). Null = from the dish-up or the pot. */
    val containerId: Id? = null,
) {
    /** A plate someone dished up for [userId] that isn't in their diary yet. */
    val isWaiting: Boolean get() = logEntryId == null && servedBy != null
}

/**
 * Grams left of the batch after these portions (never below 0). Container grams are part of
 * [Batch.cookedWeightG] and portions taken from a container count here too, so this is what is
 * left in the pot and the containers together.
 */
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

enum class StoragePlace { FRIDGE, FREEZER }

/**
 * A storage container filled at the dish-up (round 6), shared in the household. Its [grams] count
 * as dished out (part of the batch total); portions with this [id] as containerId are taken from it.
 * [number] tells containers of one batch apart ("Container 2").
 */
data class BatchContainer(
    val id: Id,
    val batchId: Id,
    val householdId: Id,
    val grams: Double,
    val place: StoragePlace,
    val number: Int,
    val deleted: Boolean = false,
)

/** Grams still in this container after the portions taken from it (never below 0). */
fun BatchContainer.gramsLeft(portions: List<BatchPortion>): Double =
    (grams - portions.filter { it.containerId == id }.sumOf { it.grams }).coerceAtLeast(0.0)

/**
 * Someone in the household, for "<name>'s plate" (round 6). [displayName] comes from their Google
 * name; blank until their app has sent it, so screens need a neutral fallback.
 */
data class HouseholdMember(val userId: Id, val displayName: String)

/** A household's pan with its empty weight (S9), so cooked pot = scale reading − pan. */
data class Pan(
    val id: Id,
    val householdId: Id,
    val name: String,
    val grams: Double,
)
