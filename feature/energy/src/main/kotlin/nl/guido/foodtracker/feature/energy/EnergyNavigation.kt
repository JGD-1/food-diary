package nl.guido.foodtracker.feature.energy

import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
            onReminders = { navController.navigate(Routes.REMINDERS) },
        )
    }
    // "weigh-in" logs a new one; "weigh-in?id=<id>" (tapped on the Weight tab) changes or deletes that one.
    composable(
        "${Routes.WEIGH_IN}?${Routes.ARG_ID}={${Routes.ARG_ID}}",
        arguments = listOf(
            navArgument(Routes.ARG_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        WeighInScreen(
            weighInId = entry.arguments?.getString(Routes.ARG_ID),
            onDone = { navController.popBackStack() },
        )
    }
}
