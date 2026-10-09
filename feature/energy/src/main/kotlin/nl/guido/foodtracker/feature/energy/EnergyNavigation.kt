package nl.guido.foodtracker.feature.energy

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.energyScreens(navController: NavController) {
    composable(Routes.ENERGY_TARGET) {
        EnergyTargetScreen(
            onBack = { navController.popBackStack() },
            onLogWeighIn = { navController.navigate(Routes.WEIGH_IN) },
        )
    }
    composable(Routes.PROFILE) {
        ProfileScreen(
            onBack = { navController.popBackStack() },
            onOpenTarget = { navController.navigate(Routes.ENERGY_TARGET) },
            onAccount = { navController.navigate(Routes.SIGN_IN) },
        )
    }
    composable(Routes.WEIGH_IN) {
        WeighInScreen(onDone = { navController.popBackStack() })
    }
}
