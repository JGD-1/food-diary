package nl.guido.foodtracker.feature.progress

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.progressScreens(navController: NavController) {
    composable(Routes.WEIGHT) {
        PlaceholderScreen(stringResource(R.string.progress_weight_title), stringResource(R.string.progress_weight_body))
    }
    composable(Routes.STATS) {
        PlaceholderScreen(stringResource(R.string.progress_stats_title), stringResource(R.string.progress_stats_body))
    }
}
