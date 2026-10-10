package nl.guido.foodtracker.feature.food

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.food.eatout.EatOutScreen

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.foodScreens(navController: NavController) {
    // Routes.eatOut(date, dish): the day to log to (missing = today) and the words to fill in.
    composable(
        route = "${Routes.EAT_OUT}?${Routes.ARG_DATE}={${Routes.ARG_DATE}}&${Routes.ARG_DISH}={${Routes.ARG_DISH}}",
        arguments = listOf(
            navArgument(Routes.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(Routes.ARG_DISH) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) {
        EatOutScreen(onDone = { navController.popBackStack() })
    }
}
