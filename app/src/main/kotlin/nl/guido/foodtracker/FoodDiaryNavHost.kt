package nl.guido.foodtracker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.camera.cameraScreens
import nl.guido.foodtracker.feature.energy.energyScreens
import nl.guido.foodtracker.feature.food.foodScreens
import nl.guido.foodtracker.feature.progress.progressScreens
import nl.guido.foodtracker.feature.recipes.recipesScreens
import nl.guido.foodtracker.feature.sync.syncScreens
import nl.guido.foodtracker.feature.today.todayScreens

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

// Icons are placeholders until core/ui gets the design's own icons.
private val tabs = listOf(
    Tab(Routes.TODAY, R.string.tab_today, Icons.Filled.Home),
    Tab(Routes.WEIGHT, R.string.tab_weight, Icons.Filled.DateRange),
    Tab(Routes.STATS, R.string.tab_stats, Icons.Filled.Info),
)

@Composable
fun FoodDiaryNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.TODAY, modifier = Modifier.padding(padding)) {
            todayScreens(navController)
            progressScreens(navController)
            foodScreens(navController)
            cameraScreens(navController)
            recipesScreens(navController)
            energyScreens(navController)
            syncScreens(navController)
        }
    }
}
