package nl.guido.foodtracker.feature.food

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.foodScreens(navController: NavController) {
    composable(Routes.EAT_OUT) {
        PlaceholderScreen(stringResource(R.string.food_eat_out_title), stringResource(R.string.food_eat_out_body))
    }
}
