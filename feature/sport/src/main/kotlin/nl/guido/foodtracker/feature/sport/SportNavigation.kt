package nl.guido.foodtracker.feature.sport

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes

/** Sport screens (round 11): the Sport tab. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.sportScreens(navController: NavController) {
    composable(Routes.SPORT) {
        SportRoute(onProfile = { navController.navigate(Routes.PROFILE) })
    }
}
