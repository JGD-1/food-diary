package nl.guido.foodtracker.feature.camera

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.core.ui.CameraResult
import nl.guido.foodtracker.core.ui.Routes

/**
 * Screens owned by this module. The app calls this once when it builds the navigation.
 * Opened as Routes.CAMERA (barcode), Routes.CAMERA_SCALE (weigh only) or Routes.CAMERA_DRINK
 * (from Drinks: a new food from a label defaults to a drink). When done it hands back
 * CameraResult.FOOD_ID (String, already saved in FoodRepository; null when only weighing) and
 * CameraResult.GRAMS (Double) on the opener's savedStateHandle, then closes.
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
                    set(CameraResult.FOOD_ID, outcome.foodId)
                    set(CameraResult.GRAMS, outcome.grams)
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
