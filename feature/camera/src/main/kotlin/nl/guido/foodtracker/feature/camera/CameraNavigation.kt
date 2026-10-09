package nl.guido.foodtracker.feature.camera

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.cameraScreens(navController: NavController) {
    composable(Routes.CAMERA) {
        PlaceholderScreen(stringResource(R.string.camera_camera_title), stringResource(R.string.camera_camera_body))
    }
}
