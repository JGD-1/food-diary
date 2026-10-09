package nl.guido.foodtracker.feature.sync

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.sync.ui.AccountScreen

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.syncScreens(navController: NavController) {
    composable(Routes.SIGN_IN) {
        AccountScreen(onClose = {
            if (!navController.popBackStack()) navController.navigate(Routes.TODAY)
        })
    }
}
