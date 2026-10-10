package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.ui.CameraResult
import nl.guido.foodtracker.core.ui.Routes
import java.time.LocalDate

/**
 * Screens inside this module. LOG and BATCH_PORTION are the shared Routes that Log food opens
 * too; the others are only opened from here.
 */
internal object RecipesRoutes {
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BATCH_ID = "batchId"
    /** Optional: the meal chosen in Log food, so the recipe or portion lands there (else by time of day). */
    const val ARG_MEAL = Routes.ARG_MEAL
    /** Optional: the day being viewed on Today (ISO), so the line lands there. Missing = today. */
    const val ARG_DATE = Routes.ARG_DATE

    const val LIST = Routes.RECIPES
    const val EDIT = "recipes/edit?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val LOG = "${Routes.RECIPE_LOG}?$ARG_MEAL={$ARG_MEAL}&$ARG_DATE={$ARG_DATE}"
    const val NEW_BATCH = "recipes/batch?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val BATCH_PORTION = "${Routes.BATCH_PORTION}?$ARG_MEAL={$ARG_MEAL}&$ARG_DATE={$ARG_DATE}"

    fun edit(recipeId: Id? = null) = if (recipeId == null) "recipes/edit" else "recipes/edit?$ARG_RECIPE_ID=$recipeId"
    fun log(recipeId: Id) = Routes.recipeLog(recipeId)
    fun newBatch(recipeId: Id? = null) = if (recipeId == null) "recipes/batch" else "recipes/batch?$ARG_RECIPE_ID=$recipeId"
    fun batchPortion(batchId: Id) = Routes.batchPortion(batchId)

    /** The meal passed by the opener, or null when none (or an unknown one) was given. */
    fun mealArg(value: String?): Meal? = Meal.entries.firstOrNull { it.name == value }

    /** The day passed by the opener, or [today] when none (or an unreadable one) was given. */
    fun dateArg(value: String?, today: LocalDate): LocalDate =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today

    /** The camera's scale reader (stream 2). It hands back grams under [CAMERA_GRAMS] before closing. */
    const val CAMERA_SCALE = Routes.CAMERA_SCALE
    const val CAMERA_GRAMS = CameraResult.GRAMS
}
