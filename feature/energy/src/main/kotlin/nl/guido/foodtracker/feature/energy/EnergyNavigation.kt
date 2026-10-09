package nl.guido.foodtracker.feature.energy

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.energyScreens(navController: NavController) {
    composable(Routes.ENERGY_TARGET) {
        PlaceholderScreen(stringResource(R.string.energy_energy_target_title), stringResource(R.string.energy_energy_target_body))
    }
    composable(Routes.PROFILE) {
        PlaceholderScreen(stringResource(R.string.energy_profile_title), stringResource(R.string.energy_profile_body))
    }
}
