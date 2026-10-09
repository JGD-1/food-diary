package nl.guido.foodtracker.feature.food

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.food.ui.EatOutScreen

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.foodScreens(navController: NavController) {
    composable(Routes.EAT_OUT) {
        EatOutScreen(onDone = { navController.popBackStack() })
    }
}
