package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.sum
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.RecipeEditViewModel
import nl.guido.foodtracker.feature.recipes.logic.filledIn
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import kotlin.math.roundToInt

/** Name, ingredients and their grams. Changes here are seen by everyone in the household. */
@Composable
internal fun RecipeEditScreen(
    navController: NavController,
    savedState: SavedStateHandle,
    /** Called with true when the recipe was deleted. */
    onDone: (deleted: Boolean) -> Unit,
    vm: RecipeEditViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    CameraGrams(savedState, vm::onWeighed)
    LaunchedEffect(state.done) { if (state.done) onDone(state.deleted) }

    Scaffold(
        topBar = {
            RecipesTopBar(
                stringResource(if (state.isNew) R.string.recipes_new_recipe else R.string.recipes_edit_recipe),
                onBack = { navController.popBackStack() },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Loading()
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                label = { Text(stringResource(R.string.recipes_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(R.string.recipes_ingredients))
            if (state.lines.isEmpty()) Hint(stringResource(R.string.recipes_ingredients_empty))
            state.lines.forEach { line ->
                IngredientLine(
                    line = line,
                    onGrams = { vm.setGrams(line.key, it) },
                    onRemove = { vm.remove(line.key) },
                    onWeigh = cameraWeigh(navController) { vm.startWeighing(line.key) },
                )
            }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipes_add_ingredient))
            }
            val filled = state.lines.filledIn()
            if (filled.isNotEmpty()) {
                Hint(
                    stringResource(
                        R.string.recipes_recipe_detail,
                        filled.map { it.nutrients }.sum().kcal.roundToInt(),
                        formatGrams(filled.sumOf { it.grams }),
                    ),
                )
            }
            Button(onClick = vm::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.recipes_save))
            }
            if (!state.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recipes_delete_recipe))
                }
            }
        }
    }

    if (picking) {
        FoodPicker(onPick = { vm.add(it); picking = false }, onClose = { picking = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.recipes_delete_question)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.recipes_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.recipes_keep)) }
            },
        )
    }
}
