package nl.guido.foodtracker.feature.reminders

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.remindersScreens(navController: NavController) {
    composable(Routes.REMINDERS) {
        RemindersScreen(onBack = { navController.popBackStack() })
    }
}
