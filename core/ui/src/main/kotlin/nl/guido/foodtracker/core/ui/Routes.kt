package nl.guido.foodtracker.core.ui

import nl.guido.foodtracker.core.model.Id

/**
 * Names of all screens, so any screen can open any other with navController.navigate(Routes.X).
 * The owning stream builds the screen; adding a route here goes through the lead.
 */
object Routes {
    // Bottom tabs
    const val TODAY = "today"
    const val WEIGHT = "weight"
    const val STATS = "stats"

    const val LOG_FOOD = "log-food"          // stream 5
    const val EAT_OUT = "eat-out"            // stream 1
    const val CAMERA = "camera"              // stream 2
    const val CAMERA_SCALE = "camera?mode=scale"
    const val CAMERA_DRINK = "camera?drink=true"
    const val RECIPES = "recipes"            // stream 3
    const val RECIPE_LOG = "recipe-log/{recipeId}"
    const val BATCH_PORTION = "batch-portion/{batchId}"
    const val ENERGY_TARGET = "energy-target" // stream 4
    const val WEIGH_IN = "weigh-in"          // stream 4 (opened from the Weight tab)
    const val PROFILE = "profile"            // stream 4 (profile) + 7 (account, export)
    const val SIGN_IN = "sign-in"            // stream 7

    fun recipeLog(recipeId: Id) = "recipe-log/$recipeId"
    fun batchPortion(batchId: Id) = "batch-portion/$batchId"
}

/** Keys the camera screen uses to hand its result back (previousBackStackEntry.savedStateHandle). */
object CameraResult {
    const val FOOD_ID = "camera_food_id"
    const val GRAMS = "camera_grams"
}
