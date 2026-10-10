package nl.guido.foodtracker.feature.reminders

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.remindersScreens(navController: NavController) {
    // Placeholder until P5 builds the reminder settings screen.
    composable(Routes.REMINDERS) {
        PlaceholderScreen(
            title = stringResource(R.string.reminders_title),
            body = stringResource(R.string.reminders_placeholder),
        )
    }
}
