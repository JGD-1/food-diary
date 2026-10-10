package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nl.guido.foodtracker.feature.recipes.LogRecipeViewModel
import nl.guido.foodtracker.feature.recipes.LogRecipeViewModel.Weighing
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.logic.formatGrams

/**
 * Log a recipe in two taps (open, Log it), or adjust it first: a saved variant, extras just for
 * this time, how much. Nothing here changes the saved recipe.
 */
@Composable
internal fun LogRecipeScreen(
    navController: NavController,
    savedState: SavedStateHandle,
    onEdit: (String) -> Unit,
    onCookBatch: (String) -> Unit,
    onDone: () -> Unit,
    vm: LogRecipeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var namingVariant by rememberSaveable { mutableStateOf(false) }
    CameraGrams(savedState, vm::onWeighed)
    LaunchedEffect(state.done) { if (state.done) onDone() }

    val recipe = state.recipe
    val dish = state.dish
    Scaffold(
        topBar = { RecipesTopBar(dish?.name ?: stringResource(R.string.recipes_recipes_title), onBack = { navController.popBackStack() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Loading()
            return@Scaffold
        }
        if (recipe == null || dish == null) {
            Hint(stringResource(R.string.recipes_not_found), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.nutrients?.let { NutrientsSummary(it) }

            if (state.variants.isNotEmpty()) {
                SectionTitle(stringResource(R.string.recipes_variant))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.variant == null,
                        onClick = { vm.chooseVariant(null) },
                        label = { Text(stringResource(R.string.recipes_variant_plain)) },
                    )
                    state.variants.forEach { v ->
                        FilterChip(selected = state.variant?.id == v.id, onClick = { vm.chooseVariant(v) }, label = { Text(v.name) })
                    }
                }
            }

            SectionTitle(stringResource(R.string.recipes_extras))
            Hint(stringResource(R.string.recipes_extras_hint))
            state.extras.forEach { line ->
                IngredientLine(
                    line = line,
                    onGrams = { vm.setExtraGrams(line.key, it) },
                    onRemove = { vm.removeExtra(line.key) },
                    onWeigh = cameraWeigh(navController) { vm.startWeighing(Weighing.Extra(line.key)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.recipes_add_extra)) }
                if (state.extras.isNotEmpty()) {
                    TextButton(onClick = { namingVariant = true }, enabled = state.canSaveVariant) { Text(stringResource(R.string.recipes_save_as_variant)) }
                }
            }

            SectionTitle(stringResource(R.string.recipes_how_much))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { vm.setGrams(formatGrams(dish.totalGrams)) },
                    label = { Text(stringResource(R.string.recipes_whole, formatGrams(dish.totalGrams))) },
                )
                state.pinned?.usualPortion?.let { usual ->
                    val grams = dish.suggestedGrams(usual)
                    AssistChip(
                        onClick = { vm.setGrams(formatGrams(grams)) },
                        label = { Text(stringResource(R.string.recipes_usual, formatGrams(grams))) },
                    )
                }
            }
            GramsField(
                value = state.gramsText,
                onValueChange = vm::setGrams,
                label = stringResource(R.string.recipes_my_portion),
                onWeigh = cameraWeigh(navController) { vm.startWeighing(Weighing.Portion) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.pinned != null && state.grams != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.rememberPortion, onCheckedChange = vm::setRememberPortion)
                    Text(stringResource(R.string.recipes_remember_portion, state.gramsText))
                }
            }

            ForDay(state.date)
            MealChips(state.meal, vm::setMeal)

            Button(onClick = vm::log, enabled = state.canLog, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.recipes_log_it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::togglePin) {
                    Text(stringResource(if (state.pinned != null) R.string.recipes_unpin else R.string.recipes_pin))
                }
                TextButton(onClick = { onEdit(recipe.id) }) { Text(stringResource(R.string.recipes_edit_recipe)) }
            }
            TextButton(onClick = { onCookBatch(recipe.id) }) { Text(stringResource(R.string.recipes_cook_batch_of_this)) }
        }
    }

    if (picking) {
        FoodPicker(onPick = { vm.addExtra(it); picking = false }, onClose = { picking = false })
    }
    if (namingVariant) {
        VariantNameDialog(
            onSave = { vm.saveAsVariant(it); namingVariant = false },
            onCancel = { namingVariant = false },
        )
    }
}

@Composable
private fun VariantNameDialog(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.recipes_save_as_variant)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(stringResource(R.string.recipes_variant_hint))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.recipes_name)) },
                    placeholder = { Text(stringResource(R.string.recipes_variant_example)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.recipes_save)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.recipes_cancel)) } },
    )
}
