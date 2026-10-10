package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.gramsLeft
import nl.guido.foodtracker.core.model.newId
import java.time.Instant
import java.time.LocalDate

internal const val DEFAULT_FOOD_GRAMS = 100.0
internal const val DEFAULT_DRINK_ML = 250.0

/** Something chosen in Log food, waiting for its amount. */
internal data class Pick(
    val name: String,
    val per100g: Nutrients,
    val what: Logged,
    val isDrink: Boolean,
    val defaultGrams: Double,
    /** Set when the food came from a search and must be stored on the phone before logging. */
    val food: Food? = null,
    /** Ready-made amounts for this food: one serving, the whole pack, a common piece. */
    val chips: List<AmountChip> = emptyList(),
) {
    /** The food behind this pick, for pinning and editing; null for recipes, batches and restaurant lines. */
    val foodId: Id? get() = (what as? Logged.FoodRef)?.foodId
}

/** A ready-made amount in the amount sheet. [label] is only set for [Kind.PIECE] ("1 apple"). */
internal data class AmountChip(val kind: Kind, val grams: Double, val label: String? = null) {
    enum class Kind { SERVING, PACK, PIECE }
}

/** "1 serving" and "Whole pack" from the food's own sizes (Open Food Facts), plus [pieces] such as "1 apple". */
internal fun amountChips(food: Food?, pieces: List<Portion> = emptyList()): List<AmountChip> {
    if (food == null) return emptyList()
    val sizes = listOfNotNull(
        food.servingG?.takeIf { it > 0 }?.let { AmountChip(AmountChip.Kind.SERVING, it) },
        food.packageG?.takeIf { it > 0 }?.let { AmountChip(AmountChip.Kind.PACK, it) },
    )
    val common = pieces.filter { it.grams > 0 && !it.label.isNullOrBlank() }
        .map { AmountChip(AmountChip.Kind.PIECE, it.grams, it.label) }
    // A pack that is one serving (a single yoghurt pot) needs only one chip.
    return (sizes + common).distinctBy { it.grams }
}

/** Foods are editable when they are ours or cached from Open Food Facts; NEVO rows must stay unchanged. */
internal fun canEdit(food: Food?): Boolean = food != null && food.source != FoodOrigin.NEVO

/** Identifies "the same thing" across diary lines, so recent items are listed once. */
internal fun itemKey(what: Logged): String = when (what) {
    is Logged.FoodRef -> "food:${what.foodId}"
    is Logged.RecipeRef -> "recipe:${what.recipeId}:${what.variantId.orEmpty()}:${what.extras.hashCode()}"
    is Logged.BatchShare -> "batch:${what.batchId}"
    is Logged.Restaurant -> "restaurant:${what.dish.trim().lowercase()}"
}

/**
 * Recent things to log again, newest first and each once. Lines from the same kind of meal
 * (drinks for Drinks, food otherwise) come first. [recent] must be newest first.
 */
internal fun recentItems(recent: List<LogEntry>, meal: Meal, limit: Int = 20): List<LogEntry> {
    val distinct = recent.distinctBy { itemKey(it.what) }
    val wantDrinks = meal == Meal.DRINKS
    return distinct.sortedBy { (it.meal == Meal.DRINKS) != wantDrinks }.take(limit)
}

/** The amount to suggest for a food: what you had last time, else a usual default. */
internal fun defaultGramsFor(foodId: Id, isDrink: Boolean, recent: List<LogEntry>): Double =
    recent.firstOrNull { (it.what as? Logged.FoodRef)?.foodId == foodId && it.portion.grams > 0 }?.portion?.grams
        ?: if (isDrink) DEFAULT_DRINK_ML else DEFAULT_FOOD_GRAMS

internal fun pickFromFood(food: Food, recent: List<LogEntry>, pieces: List<Portion> = emptyList()): Pick = Pick(
    name = food.name,
    per100g = food.per100g,
    what = Logged.FoodRef(food.id, food.name),
    isDrink = food.isDrink,
    defaultGrams = defaultGramsFor(food.id, food.isDrink, recent),
    food = food,
    chips = amountChips(food, pieces),
)

/** A recent line as something to log with a new amount; null for lines without a weight. */
internal fun pickFromEntry(entry: LogEntry): Pick? {
    if (!canChangeAmount(entry)) return null
    return Pick(
        name = entry.displayName,
        per100g = entry.nutrients * (100.0 / entry.portion.grams),
        what = entry.what,
        isDrink = entry.meal == Meal.DRINKS,
        defaultGrams = entry.portion.grams,
    )
}

internal fun entryFromPick(
    pick: Pick,
    grams: Double,
    userId: Id,
    date: LocalDate,
    meal: Meal,
    now: Instant,
    id: Id = newId(),
): LogEntry = LogEntry(
    id = id,
    userId = userId,
    date = date,
    meal = meal,
    what = pick.what,
    portion = Portion(grams),
    nutrients = pick.per100g.forGrams(grams),
    isEstimate = pick.what is Logged.Restaurant,
    createdAt = now,
)

/** Logs a recent line again, exactly as it was ("one-tap repeat"). */
internal fun repeatEntry(
    last: LogEntry,
    userId: Id,
    date: LocalDate,
    meal: Meal,
    now: Instant,
    id: Id = newId(),
): LogEntry = last.copy(id = id, userId = userId, date = date, meal = meal, createdAt = now)

/** Drinks always go to the Drinks section, so soft drinks and alcohol are counted together. */
internal fun mealFor(isDrink: Boolean, chosen: Meal): Meal = if (isDrink) Meal.DRINKS else chosen

/** A food I pinned, with my usual amount. */
internal data class PinnedFood(val favourite: Favourite, val food: Food) {
    val usualGrams: Double? get() = favourite.usualPortion?.grams?.takeIf { it > 0 }
}

/** My pinned foods (favourites with a food), A to Z; foods no longer on the phone drop out. */
internal suspend fun pinnedFoods(favourites: List<Favourite>, lookup: suspend (Id) -> Food?): List<PinnedFood> =
    favourites.filter { it.foodId != null }
        .distinctBy { it.foodId }
        .mapNotNull { fav -> lookup(fav.foodId!!)?.let { PinnedFood(fav, it) } }
        .sortedBy { it.food.name.lowercase() }

/** Pins a food with the amount in the sheet as my usual amount. */
internal fun foodFavourite(userId: Id, foodId: Id, grams: Double, id: Id = newId()): Favourite =
    Favourite(id = id, userId = userId, foodId = foodId, usualPortion = Portion(grams))

/** A batch still in the pot (nobody tapped "Finished"), with what is left across the household. */
internal data class PotItem(val batch: Batch, val gramsLeft: Double)

/** "From the pot": unfinished batches, newest first, with grams left after everyone's portions. */
internal fun potItems(batches: List<Batch>, portions: List<BatchPortion>): List<PotItem> {
    val byBatch = portions.groupBy { it.batchId }
    return batches.filter { it.finishedOn == null }
        .sortedByDescending { it.cookedOn }
        .map { PotItem(it, it.gramsLeft(byBatch[it.id].orEmpty())) }
}

/** Favourites are per person: each becomes its recipe with my usual portion, A to Z. Removed recipes drop out. */
internal fun pinnedRecipes(favourites: List<Favourite>, recipes: List<Recipe>): List<Recipe> {
    val byId = recipes.associateBy { it.id }
    return favourites.mapNotNull { fav ->
        fav.recipeId?.let { byId[it] }?.copy(pinned = true, usualPortion = fav.usualPortion)
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}

/** A food the camera scanned and weighed, as a diary line: the amount was already chosen there. */
internal fun cameraEntry(
    food: Food,
    grams: Double,
    userId: Id,
    date: LocalDate,
    meal: Meal,
    now: Instant,
    id: Id = newId(),
): LogEntry = entryFromPick(pickFromFood(food, emptyList()), grams, userId, date, meal, now, id)
