package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
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
)

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

internal fun pickFromFood(food: Food, recent: List<LogEntry>): Pick = Pick(
    name = food.name,
    per100g = food.per100g,
    what = Logged.FoodRef(food.id, food.name),
    isDrink = food.isDrink,
    defaultGrams = defaultGramsFor(food.id, food.isDrink, recent),
    food = food,
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

/** Favourites are per person: each becomes its recipe with my usual portion, A to Z. Removed recipes drop out. */
internal fun pinnedRecipes(favourites: List<Favourite>, recipes: List<Recipe>): List<Recipe> {
    val byId = recipes.associateBy { it.id }
    return favourites.mapNotNull { fav ->
        byId[fav.recipeId]?.copy(pinned = true, usualPortion = fav.usualPortion)
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}
