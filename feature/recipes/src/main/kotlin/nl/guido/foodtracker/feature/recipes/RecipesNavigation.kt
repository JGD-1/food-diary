package nl.guido.foodtracker.feature.recipes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.feature.recipes.ui.BatchPortionScreen
import nl.guido.foodtracker.feature.recipes.ui.LogRecipeScreen
import nl.guido.foodtracker.feature.recipes.ui.NewBatchScreen
import nl.guido.foodtracker.feature.recipes.ui.RecipeEditScreen
import nl.guido.foodtracker.feature.recipes.ui.RecipesScreen

/** Screens owned by this module. The app calls this once when it builds the navigation. */
fun NavGraphBuilder.recipesScreens(navController: NavController) {
    val optionalRecipeId = listOf(
        navArgument(RecipesRoutes.ARG_RECIPE_ID) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )
    val optionalMeal = navArgument(RecipesRoutes.ARG_MEAL) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }

    composable(RecipesRoutes.LIST) {
        RecipesScreen(
            onBack = { navController.popBackStack() },
            onLog = { navController.navigate(RecipesRoutes.log(it)) },
            onEdit = { navController.navigate(RecipesRoutes.edit(it)) },
            onNewBatch = { navController.navigate(RecipesRoutes.newBatch()) },
            onBatchPortion = { navController.navigate(RecipesRoutes.batchPortion(it)) },
        )
    }
    composable(RecipesRoutes.EDIT, arguments = optionalRecipeId) { entry ->
        RecipeEditScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onDone = { deleted ->
                // A deleted recipe can't be shown any more, so go back past its log screen to the list.
                if (!deleted || !navController.popBackStack(RecipesRoutes.LIST, inclusive = false)) {
                    navController.popBackStack()
                }
            },
        )
    }
    composable(RecipesRoutes.LOG, arguments = listOf(navArgument(RecipesRoutes.ARG_RECIPE_ID) { type = NavType.StringType }, optionalMeal)) { entry ->
        LogRecipeScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onEdit = { navController.navigate(RecipesRoutes.edit(it)) },
            onCookBatch = { navController.navigate(RecipesRoutes.newBatch(it)) },
            onDone = { navController.popBackStack() },
        )
    }
    composable(RecipesRoutes.NEW_BATCH, arguments = optionalRecipeId) { entry ->
        NewBatchScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onSaved = { batchId ->
                // Step 3 replaces the cooking screen, so Back from it doesn't reopen a saved batch.
                navController.navigate(RecipesRoutes.batchPortion(batchId)) {
                    popUpTo(RecipesRoutes.NEW_BATCH) { inclusive = true }
                }
            },
        )
    }
    composable(RecipesRoutes.BATCH_PORTION, arguments = listOf(navArgument(RecipesRoutes.ARG_BATCH_ID) { type = NavType.StringType }, optionalMeal)) { entry ->
        BatchPortionScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onDone = { navController.popBackStack() },
        )
    }
}
