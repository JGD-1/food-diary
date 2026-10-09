package nl.guido.foodtracker.feature.sync

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.syncScreens(navController: NavController) {
    composable(Routes.SIGN_IN) {
        PlaceholderScreen(stringResource(R.string.sync_sign_in_title), stringResource(R.string.sync_sign_in_body))
    }
}
