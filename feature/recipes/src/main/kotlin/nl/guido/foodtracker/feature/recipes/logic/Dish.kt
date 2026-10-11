package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import nl.guido.foodtracker.core.model.gramsLeft
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.core.model.sum
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A recipe as it is made this time: the saved recipe, plus a saved variant's extras,
 * plus extras added on the go. The saved recipe itself never changes.
 *
 * You eat a share of the dish. The share stays the same when extras are added, so
 * "the whole recipe plus extra cheese" still means the whole thing, cheese included.
 */
data class Dish(
    val recipe: Recipe,
    val variant: RecipeVariant? = null,
    val extras: List<Ingredient> = emptyList(),
) {
    val ingredients: List<Ingredient> get() = recipe.ingredients + variant?.extras.orEmpty() + extras
    /**
     * Grams of the whole dish as eaten: the recipe's weight after cooking when known (S12),
     * plus the extras, which go on as they are.
     */
    val totalGrams: Double get() = recipe.dishGrams + (variant?.extras.orEmpty() + extras).sumOf { it.grams }
    val total: Nutrients get() = ingredients.map { it.nutrients }.sum()
    /** "Pasta pesto" or, for a variant, "Pasta pesto (extra cheese)". */
    val name: String get() = variant?.let { "${recipe.name} (${it.name})" } ?: recipe.name

    /** Nutrients in [grams] of this dish. */
    fun nutrientsFor(grams: Double): Nutrients =
        if (totalGrams <= 0.0) Nutrients.ZERO else total * (grams / totalGrams)

    /**
     * Grams to suggest when logging: the usual portion if there is one (remembered as a
     * share of the plain recipe), otherwise the whole dish.
     */
    fun suggestedGrams(usual: Portion?): Double {
        val base = recipe.dishGrams
        val share = if (usual == null || base <= 0.0) 1.0 else usual.grams / base
        return share * totalGrams
    }

    /** Turns grams of this dish back into grams of the plain recipe, to remember as the usual portion. */
    fun toUsualPortion(grams: Double): Portion? {
        if (totalGrams <= 0.0) return null
        return Portion(grams / totalGrams * recipe.dishGrams)
    }

    /** Grams in [count] pieces when the recipe makes pieces (S11), else null. */
    fun piecesGrams(count: Double): Double? =
        recipe.pieces?.takeIf { it > 0 }?.let { totalGrams / it * count }

    /** A new saved variant: this variant's extras (if any) plus the extras added now. */
    fun asNewVariant(name: String, id: Id = newId()): RecipeVariant =
        RecipeVariant(id = id, baseRecipeId = recipe.id, name = name, extras = variant?.extras.orEmpty() + extras)

    fun logEntry(
        userId: Id, date: LocalDate, meal: Meal, grams: Double, now: Instant, label: String? = null, id: Id = newId(),
    ) = LogEntry(
        id = id,
        userId = userId,
        date = date,
        meal = meal,
        what = Logged.RecipeRef(recipeId = recipe.id, name = name, variantId = variant?.id, extras = extras),
        portion = Portion(grams, label),
        nutrients = nutrientsFor(grams),
        isEstimate = false,
        createdAt = now,
    )
}

/** Grams of the whole dish as eaten: the weight after cooking or baking when known (S12), else the ingredients. */
val Recipe.dishGrams: Double get() = cookedWeightG?.takeIf { it > 0.0 } ?: totalGrams

/** "½", "1", "2" pieces: the chips offered when a recipe or batch makes pieces (S11). */
val PIECE_CHOICES: List<Double> = listOf(0.5, 1.0, 2.0)

/** Grams in [count] pieces of a batch that makes pieces (S11), else null. */
fun Batch.piecesGrams(count: Double): Double? =
    pieces?.takeIf { it > 0 && cookedWeightG > 0.0 }?.let { cookedWeightG / it * count }

/** My weighed portion of a batch, as a diary line. */
fun Batch.logEntry(
    userId: Id, date: LocalDate, meal: Meal, grams: Double, now: Instant, label: String? = null, id: Id = newId(),
) = LogEntry(
    id = id,
    userId = userId,
    date = date,
    meal = meal,
    what = Logged.BatchShare(batchId = this.id, name = name),
    portion = Portion(grams, label),
    nutrients = shareFor(grams),
    isEstimate = false,
    createdAt = now,
)

/** The meal that fits the time of day, used as the first choice on the log screens. */
fun mealAt(time: LocalTime): Meal = when {
    time < LocalTime.of(10, 30) -> Meal.BREAKFAST
    time < LocalTime.of(14, 30) -> Meal.LUNCH
    time < LocalTime.of(17, 0) -> Meal.SNACKS
    time < LocalTime.of(21, 0) -> Meal.DINNER
    else -> Meal.SNACKS
}

/** Reads grams typed by a person: accepts "250", "250.5" and the Dutch "250,5". Null if not a positive number. */
fun parseGrams(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

/** Reads "Makes … pieces": a whole number from 1 to 500, else null. */
fun parsePieces(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..500 }

/** A batch on the list with what is left in the pot, counting everyone's portions in the household. */
data class OpenBatch(val batch: Batch, val gramsLeft: Double)

/** Batches nobody has marked Finished yet, newest first, with grams left from everyone's portions. */
fun openBatches(batches: List<Batch>, portions: List<BatchPortion>): List<OpenBatch> {
    val byBatch = portions.groupBy { it.batchId }
    return batches.filter { it.finishedOn == null }
        .sortedByDescending { it.cookedOn }
        .map { OpenBatch(it, it.gramsLeft(byBatch[it.id].orEmpty())) }
}

/** My portion as the grams-only row the household sees, linked to my diary line. */
fun Batch.portionFor(entry: LogEntry, householdId: Id, id: Id = newId()) = BatchPortion(
    id = id,
    batchId = this.id,
    householdId = householdId,
    userId = entry.userId,
    grams = entry.portion.grams,
    logEntryId = entry.id,
)
