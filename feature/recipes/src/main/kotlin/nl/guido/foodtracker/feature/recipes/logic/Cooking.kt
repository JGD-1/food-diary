package nl.guido.foodtracker.feature.recipes.logic

import nl.guido.foodtracker.core.model.HouseholdMember
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Recipe
import kotlin.math.roundToInt

/**
 * Round 16: cooking is one screen, "Koken", in three steps: what goes in, dish up, done.
 * The step bar at the top shows where you are; step 1 and 2 can be tapped to go back and forth.
 */
enum class CookStep { IN, DISH_UP, DONE }

/** Which step the cooking screen shows. */
fun cookStep(dishingUp: Boolean, dishedUp: Boolean): CookStep = when {
    dishedUp -> CookStep.DONE
    dishingUp -> CookStep.DISH_UP
    else -> CookStep.IN
}

/**
 * The rows dish up starts with (Round 16): my plate, a plate for everyone else in the household,
 * and one fridge box. A row left empty is simply not saved, so an unused box costs nothing.
 */
fun cookingStartRows(myUserId: Id, members: List<HouseholdMember>): List<DishRow> =
    startingRows(myUserId, members) + DishRow(kind = DishKind.FRIDGE)

/** One line on the Done step: what happened to a plate or box. */
data class DoneLine(
    val kind: DishKind,
    /** Whose plate, for [DishKind.PLATE]: null = mine. */
    val forUserId: Id?,
    val grams: Double,
    val kcal: Int,
)

/**
 * The Done step's list: every weighed plate and box with its share of [raw] (what went in),
 * my plates first, then other people's plates, guests, then boxes. A guess of the rest in the pot
 * ([restText]) is one more fridge box, as [dishOutcome] saves it.
 */
fun doneLines(myUserId: Id, rows: List<DishRow>, restText: String, raw: Nutrients): List<DoneLine> {
    val total = dishedTotal(rows, restText)
    val weighed = rows.mapNotNull { row ->
        val grams = row.grams?.takeIf { it > 0.0 } ?: return@mapNotNull null
        val forUser = row.forUserId?.takeIf { row.kind == DishKind.PLATE && it != myUserId }
        DoneLine(row.kind, forUser, grams, shareOf(raw, grams, total).kcal.roundToInt())
    } + listOfNotNull(
        parseGrams(restText)?.takeIf { it > 0.0 }?.let { DoneLine(DishKind.FRIDGE, null, it, shareOf(raw, it, total).kcal.roundToInt()) },
    )
    fun order(line: DoneLine) = when {
        line.kind == DishKind.PLATE && line.forUserId == null -> 0
        line.kind == DishKind.PLATE -> 1
        line.kind == DishKind.GUEST -> 2
        else -> 3
    }
    return weighed.sortedBy(::order)
}

/**
 * "Save as recipe" on the Done step, for a cook that didn't start from a recipe: the amounts that
 * went in, under the dish's name. Null when there is no name or nothing went in.
 */
fun recipeFromCook(id: Id, householdId: Id, name: String, ingredients: List<Ingredient>, pieces: Int?): Recipe? {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || ingredients.isEmpty()) return null
    return Recipe(id = id, householdId = householdId, name = trimmed, ingredients = ingredients, pieces = pieces)
}

/** "Uit een recept": the recipe's lines go below what is already in the pot, at the recipe's own amounts. */
fun withRecipeLines(current: List<EditableIngredient>, recipe: Recipe): List<EditableIngredient> =
    current + recipe.ingredients.map { EditableIngredient.of(it) }
