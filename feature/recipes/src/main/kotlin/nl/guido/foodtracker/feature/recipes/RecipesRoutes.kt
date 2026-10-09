package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.ui.Routes

/**
 * Screens inside this module. RECIPE_LOG and BATCH_PORTION match the strings the lead is adding
 * to core/ui Routes.kt (decisions.md, 9 Oct), so Log food can open them; the others are only
 * opened from here.
 */
internal object RecipesRoutes {
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BATCH_ID = "batchId"

    const val LIST = Routes.RECIPES
    const val EDIT = "recipes/edit?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val LOG = "recipe-log/{$ARG_RECIPE_ID}"
    const val NEW_BATCH = "recipes/batch?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val BATCH_PORTION = "batch-portion/{$ARG_BATCH_ID}"

    fun edit(recipeId: Id? = null) = if (recipeId == null) "recipes/edit" else "recipes/edit?$ARG_RECIPE_ID=$recipeId"
    fun log(recipeId: Id) = "recipe-log/$recipeId"
    fun newBatch(recipeId: Id? = null) = if (recipeId == null) "recipes/batch" else "recipes/batch?$ARG_RECIPE_ID=$recipeId"
    fun batchPortion(batchId: Id) = "batch-portion/$batchId"

    /** The camera's scale reader (stream 2). It hands back grams under [CAMERA_GRAMS] before closing. */
    const val CAMERA_SCALE = "camera?mode=scale"
    const val CAMERA_GRAMS = "camera_grams"
}
