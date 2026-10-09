package nl.guido.foodtracker.feature.today

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import nl.guido.foodtracker.core.ui.PlaceholderScreen
import nl.guido.foodtracker.core.ui.Routes

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.todayScreens(navController: NavController) {
    composable(Routes.TODAY) {
        TodayScreen(onLogFood = { navController.navigate(Routes.LOG_FOOD) })
    }
    composable(Routes.LOG_FOOD) {
        PlaceholderScreen(stringResource(R.string.today_log_food_title), stringResource(R.string.today_log_food_body))
    }
}
