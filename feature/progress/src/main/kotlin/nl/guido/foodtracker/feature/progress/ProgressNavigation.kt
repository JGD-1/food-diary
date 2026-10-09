package nl.guido.foodtracker.feature.progress

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.progress.stats.StatsRoute
import nl.guido.foodtracker.feature.progress.weight.WeightRoute

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.progressScreens(navController: NavController) {
    composable(Routes.WEIGHT) {
        WeightRoute(
            onLogWeighIn = { navController.navigate(Routes.WEIGH_IN) },
            onSetTarget = { navController.navigate(Routes.PROFILE) },
        )
    }
    composable(Routes.STATS) {
        StatsRoute()
    }
}
