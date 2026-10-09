package nl.guido.foodtracker.feature.camera

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.core.ui.Routes

// Result keys and modes, as agreed with the lead (decisions.md). Callers read the result from
// their own back stack entry's savedStateHandle after the camera screen closes.
internal const val RESULT_FOOD_ID = "camera_food_id" // String: the food, already saved in FoodRepository
internal const val RESULT_GRAMS = "camera_grams"     // Double: grams weighed or typed

/**
 * Screens owned by this module. The app calls this once when it builds the navigation.
 * Opened as Routes.CAMERA (barcode), "camera?mode=scale" (weigh only), "camera?mode=label"
 * or "camera?drink=true" (from Drinks: a new food from a label defaults to a drink).
 */
fun NavGraphBuilder.cameraScreens(navController: NavController) {
    composable(
        route = "${Routes.CAMERA}?${CameraViewModel.ARG_MODE}={${CameraViewModel.ARG_MODE}}" +
            "&${CameraViewModel.ARG_DRINK}={${CameraViewModel.ARG_DRINK}}",
        arguments = listOf(
            navArgument(CameraViewModel.ARG_MODE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(CameraViewModel.ARG_DRINK) { type = NavType.BoolType; defaultValue = false },
        ),
    ) {
        CameraScreen(
            onFinished = { outcome ->
                navController.previousBackStackEntry?.savedStateHandle?.apply {
                    set(RESULT_FOOD_ID, outcome.foodId)
                    set(RESULT_GRAMS, outcome.grams)
                }
                navController.popBackStack()
            },
            onLeave = { navController.popBackStack() },
            onSearchByName = {
                val cameFromLogFood = navController.previousBackStackEntry?.destination?.route == Routes.LOG_FOOD
                navController.popBackStack()
                if (!cameFromLogFood) navController.navigate(Routes.LOG_FOOD)
            },
        )
    }
}
