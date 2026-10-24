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
    // "Edit food" (Routes.foodEdit(id)): the camera's food form, pre-filled; closes when saved or deleted.
    composable(
        route = Routes.FOOD_EDIT,
        arguments = listOf(navArgument(Routes.ARG_FOOD_ID) { type = NavType.StringType }),
    ) {
        FoodEditScreen(
            onDone = { navController.popBackStack() },
            onScanAgain = { id -> navController.navigate(CameraViewModel.relabelRoute(id)) },
        )
    }
    composable(
        route = "${Routes.CAMERA}?${CameraViewModel.ARG_MODE}={${CameraViewModel.ARG_MODE}}" +
            "&${CameraViewModel.ARG_DRINK}={${CameraViewModel.ARG_DRINK}}" +
            "&${CameraViewModel.ARG_REPLACE}={${CameraViewModel.ARG_REPLACE}}",
        arguments = listOf(
            navArgument(CameraViewModel.ARG_MODE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(CameraViewModel.ARG_DRINK) { type = NavType.BoolType; defaultValue = false },
            navArgument(CameraViewModel.ARG_REPLACE) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        val replacing = entry.arguments?.getString(CameraViewModel.ARG_REPLACE) != null
        CameraScreen(
            onFinished = { outcome ->
                // A label scanned again replaced the food: close Edit food too, like its own save.
                if (replacing) navController.popBackStack(Routes.FOOD_EDIT, inclusive = true)
                else {
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(CameraResult.FOOD_ID, outcome.foodId)
                        set(CameraResult.GRAMS, outcome.grams)
                    }
                    navController.popBackStack()
                }
            },
            onLeave = { navController.popBackStack() },
            canName = logsFoodItself(navController.previousBackStackEntry?.destination?.route),
            onSearchByName = {
                val cameFromLogFood = isLogFoodRoute(navController.previousBackStackEntry?.destination?.route)
                navController.popBackStack()
                if (!cameFromLogFood) navController.navigate(Routes.LOG_FOOD)
            },
        )
    }
}

/** Log food is registered with optional extras ("log-food?meal={meal}&…"), so match the start. */
internal fun isLogFoodRoute(route: String?): Boolean =
    route == Routes.LOG_FOOD || route?.startsWith(Routes.LOG_FOOD + "?") == true

/** Today (any day) and Log food log a scanned food themselves; recipes only want the grams. */
internal fun logsFoodItself(route: String?): Boolean =
    isLogFoodRoute(route) || route == Routes.TODAY || route == Routes.DAY
