package nl.guido.foodtracker

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import nl.guido.foodtracker.core.ui.FoodIcons
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.camera.cameraScreens
import nl.guido.foodtracker.feature.energy.energyScreens
import nl.guido.foodtracker.feature.food.foodScreens
import nl.guido.foodtracker.feature.progress.progressScreens
import nl.guido.foodtracker.feature.recipes.recipesScreens
import nl.guido.foodtracker.feature.reminders.remindersScreens
import nl.guido.foodtracker.feature.sport.sportScreens
import nl.guido.foodtracker.feature.sync.syncScreens
import nl.guido.foodtracker.feature.today.todayScreens
import nl.guido.foodtracker.feature.update.UpdateCard
import nl.guido.foodtracker.feature.update.updateAndStart
import nl.guido.foodtracker.feature.update.updateScreens

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

// Round 17 (P7): each icon matches its label.
private val tabs = listOf(
    Tab(Routes.TODAY, R.string.tab_today, Icons.Filled.Home),
    Tab(Routes.WEIGHT, R.string.tab_weight, FoodIcons.Scale),
    Tab(Routes.STATS, R.string.tab_stats, FoodIcons.Bars),
    Tab(Routes.SPORT, R.string.tab_sport, FoodIcons.Run),
)

@Composable
fun FoodDiaryNavHost(
    shouldOfferSignIn: Flow<Boolean>,
    openRoute: String? = null,
    onRouteOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    // Offer sign-in once at start-up. The sign-in screen has "Not now", so it never blocks the app.
    LaunchedEffect(Unit) {
        if (openRoute == null && shouldOfferSignIn.first()) navController.navigate(Routes.SIGN_IN)
    }
    // A notification (e.g. the lunch reminder) asked for a screen: open it on top of Today.
    LaunchedEffect(openRoute) {
        if (openRoute != null) {
            runCatching { navController.navigate(openRoute) }
            onRouteOpened()
        }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                TabBar(currentRoute) { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Round 13 + 17 (P14): a calm row at the top of Today when the home computer has a newer version
            // (checked at most once a day). It no longer floats over other screens or the main button.
            if (currentRoute == Routes.TODAY) {
                UpdateCard(
                    onUpdate = { navController.navigate(updateAndStart()) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            NavHost(navController, startDestination = Routes.TODAY, modifier = Modifier.weight(1f)) {
                todayScreens(navController)
                progressScreens(navController)
                foodScreens(navController)
                cameraScreens(navController)
                recipesScreens(navController)
                energyScreens(navController)
                syncScreens(navController)
                remindersScreens(navController)
                sportScreens(navController)
                updateScreens(navController)
            }
        }
    }
}

/**
 * Garden tab bar: a slim white bar with rounded top corners; the open tab sits in a soft green pill.
 * Slimmer than Material's NavigationBar (Guido, 18 Oct), still at least 56 dp so tabs are easy to tap.
 */
private const val MAX_TAB_FONT_SCALE = 1.15f

@Composable
private fun TabBar(currentRoute: String?, onTab: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab) })
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    // N3: at the largest font the label stops growing, so "Vandaag" and "Gewicht" stay whole.
                    val fontScale = LocalDensity.current.fontScale
                    val base = MaterialTheme.typography.labelMedium
                    val style = if (fontScale > MAX_TAB_FONT_SCALE) base.copy(fontSize = base.fontSize * (MAX_TAB_FONT_SCALE / fontScale)) else base
                    Text(stringResource(tab.label), style = style, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
