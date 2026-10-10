package nl.guido.foodtracker.feature.today

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.todayScreens(navController: NavController) {
    composable(Routes.TODAY) { entry ->
        TodayRoute(navController, entry)
    }
    // Another day, opened from Stats; the arrows on it move between days like on Today.
    composable(
        Routes.DAY,
        arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.StringType }),
    ) { entry ->
        TodayRoute(navController, entry)
    }
    composable(
        TodayRoutes.LOG_FOOD_PATTERN,
        arguments = listOf(TodayRoutes.ARG_MEAL, TodayRoutes.ARG_FOOD_ID, TodayRoutes.ARG_GRAMS, TodayRoutes.ARG_DATE).map { name ->
            navArgument(name) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        },
    ) { entry ->
        LogFoodRoute(navController, entry)
    }
}
