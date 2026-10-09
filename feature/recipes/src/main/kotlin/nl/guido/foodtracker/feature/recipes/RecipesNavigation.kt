package nl.guido.foodtracker.feature.recipes

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.recipesScreens(navController: NavController) {
    composable(Routes.RECIPES) {
        PlaceholderScreen(stringResource(R.string.recipes_recipes_title), stringResource(R.string.recipes_recipes_body))
    }
}
