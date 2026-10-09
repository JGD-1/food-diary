package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.ui.CameraResult
import nl.guido.foodtracker.core.ui.Routes

/**
 * Screens inside this module. LOG and BATCH_PORTION are the shared Routes that Log food opens
 * too; the others are only opened from here.
 */
internal object RecipesRoutes {
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BATCH_ID = "batchId"

    const val LIST = Routes.RECIPES
    const val EDIT = "recipes/edit?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val LOG = Routes.RECIPE_LOG
    const val NEW_BATCH = "recipes/batch?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val BATCH_PORTION = Routes.BATCH_PORTION

    fun edit(recipeId: Id? = null) = if (recipeId == null) "recipes/edit" else "recipes/edit?$ARG_RECIPE_ID=$recipeId"
    fun log(recipeId: Id) = Routes.recipeLog(recipeId)
    fun newBatch(recipeId: Id? = null) = if (recipeId == null) "recipes/batch" else "recipes/batch?$ARG_RECIPE_ID=$recipeId"
    fun batchPortion(batchId: Id) = Routes.batchPortion(batchId)

    /** The camera's scale reader (stream 2). It hands back grams under [CAMERA_GRAMS] before closing. */
    const val CAMERA_SCALE = Routes.CAMERA_SCALE
    const val CAMERA_GRAMS = CameraResult.GRAMS
}
