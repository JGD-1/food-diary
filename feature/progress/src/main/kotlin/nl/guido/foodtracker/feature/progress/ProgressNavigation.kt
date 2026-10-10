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
            onOpenWeighIn = { id -> navController.navigate(Routes.weighIn(id)) },
        )
    }
    composable(Routes.STATS) {
        StatsRoute(
            // Today registers the day route (finding 1); until it does, tapping a day simply does nothing.
            onOpenDay = { date -> runCatching { navController.navigate(Routes.day(date)) } },
        )
    }
}
