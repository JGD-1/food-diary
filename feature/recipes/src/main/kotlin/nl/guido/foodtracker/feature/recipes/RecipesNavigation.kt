package nl.guido.foodtracker.feature.recipes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nl.guido.foodtracker.core.ui.FoodPickPurpose
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.recipes.note.NoteScanScreen
import nl.guido.foodtracker.feature.recipes.ui.BatchPortionScreen
import nl.guido.foodtracker.feature.recipes.ui.FridgeScreen
import nl.guido.foodtracker.feature.recipes.ui.LogRecipeScreen
import nl.guido.foodtracker.feature.recipes.ui.NewBatchScreen
import nl.guido.foodtracker.feature.recipes.ui.RecipeEditScreen
import nl.guido.foodtracker.feature.recipes.ui.RecipesScreen
import java.time.LocalDate

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
    val optionalDate = navArgument(RecipesRoutes.ARG_DATE) {
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
            onEditBatch = { navController.navigate(RecipesRoutes.batchEdit(it)) },
            onFridge = { navController.navigate(RecipesRoutes.FRIDGE) },
        )
    }
    composable(RecipesRoutes.FRIDGE) {
        FridgeScreen(
            onBack = { navController.popBackStack() },
            onWeigh = { batchId, boxId -> navController.navigate(RecipesRoutes.boxPortion(batchId, boxId)) },
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
    composable(RecipesRoutes.LOG, arguments = listOf(navArgument(RecipesRoutes.ARG_RECIPE_ID) { type = NavType.StringType }, optionalMeal, optionalDate)) { entry ->
        LogRecipeScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onEdit = { navController.navigate(RecipesRoutes.edit(it)) },
            onCookBatch = { recipeId, date ->
                navController.navigate(RecipesRoutes.newBatch(recipeId, date.takeIf { it != LocalDate.now() }))
            },
            onDone = { navController.popBackStack() },
        )
    }
    composable(RecipesRoutes.NEW_BATCH, arguments = optionalRecipeId + optionalDate) { entry ->
        NewBatchScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onSaved = { batchId, cookedOn ->
                // Step 3 replaces the cooking screen, so Back from it doesn't reopen a saved batch.
                // The first portion lands on the cook day.
                val date = cookedOn.takeIf { it != LocalDate.now() }
                navController.navigate(Routes.batchPortion(batchId, date = date)) {
                    popUpTo(RecipesRoutes.NEW_BATCH) { inclusive = true }
                }
            },
            // Dished up (round 6): my plate is logged, the rest waits or is in the fridge.
            onDishedUp = { navController.popBackStack() },
        )
    }
    val optionalBox = navArgument(RecipesRoutes.ARG_BOX) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }
    composable(RecipesRoutes.BATCH_PORTION, arguments = listOf(navArgument(RecipesRoutes.ARG_BATCH_ID) { type = NavType.StringType }, optionalMeal, optionalDate, optionalBox)) { entry ->
        BatchPortionScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onDone = { navController.popBackStack() },
            onEdit = { navController.navigate(RecipesRoutes.batchEdit(it)) },
        )
    }
    composable(
        RecipesRoutes.NOTE_SCAN,
        arguments = listOf(navArgument(RecipesRoutes.ARG_PURPOSE) { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        val purpose = FoodPickPurpose.entries.firstOrNull { it.name == entry.arguments?.getString(RecipesRoutes.ARG_PURPOSE) }
            ?: FoodPickPurpose.BATCH_INGREDIENT
        NoteScanScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            purpose = purpose,
            onDone = { result ->
                navController.previousBackStackEntry?.savedStateHandle?.set(RecipesRoutes.NOTE_RESULT, result)
                navController.popBackStack()
            },
        )
    }
    composable(RecipesRoutes.BATCH_EDIT, arguments = listOf(navArgument(RecipesRoutes.ARG_BATCH_ID) { type = NavType.StringType })) { entry ->
        NewBatchScreen(
            navController = navController,
            savedState = entry.savedStateHandle,
            onSaved = { _, _ -> },
            onClosed = { deleted ->
                if (deleted) {
                    // A deleted batch can't be shown any more, so go back past its portion screen too.
                    if (!navController.popBackStack(RecipesRoutes.BATCH_PORTION, inclusive = true)) navController.popBackStack()
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.set(RecipesRoutes.BATCH_CHANGED, true)
                    navController.popBackStack()
                }
            },
        )
    }
}
