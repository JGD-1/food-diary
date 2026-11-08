package nl.guido.foodtracker.feature.update

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.core.ui.Routes

/** Round 13: Profile > Updates. "update?start=true" (from the start-up card) starts the download at once. */
fun NavGraphBuilder.updateScreens(navController: NavController) {
    composable(
        "${Routes.UPDATE}?$ARG_START={$ARG_START}",
        arguments = listOf(navArgument(ARG_START) { type = NavType.BoolType; defaultValue = false }),
    ) { entry ->
        UpdateScreen(
            onBack = { navController.popBackStack() },
            startDownload = entry.arguments?.getBoolean(ARG_START) ?: false,
        )
    }
}

/** Route for the start-up card's "Bijwerken". */
fun updateAndStart(): String = "${Routes.UPDATE}?$ARG_START=true"

private const val ARG_START = "start"
