package nl.guido.foodtracker.feature.energy

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes

/**
 * Route of the weekly weigh-in screen. Kept here until the lead adds Routes.WEIGH_IN with the same
 * text, so the Weight tab (stream 6) and Today (stream 5) can open it too.
 */
internal const val WEIGH_IN_ROUTE = "weigh-in"

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.energyScreens(navController: NavController) {
    composable(Routes.ENERGY_TARGET) {
        EnergyTargetScreen(
            onBack = { navController.popBackStack() },
            onLogWeighIn = { navController.navigate(WEIGH_IN_ROUTE) },
        )
    }
    composable(Routes.PROFILE) {
        ProfileScreen(
            onBack = { navController.popBackStack() },
            onOpenTarget = { navController.navigate(Routes.ENERGY_TARGET) },
            onAccount = { navController.navigate(Routes.SIGN_IN) },
        )
    }
    composable(WEIGH_IN_ROUTE) {
        WeighInScreen(onDone = { navController.popBackStack() })
    }
}
