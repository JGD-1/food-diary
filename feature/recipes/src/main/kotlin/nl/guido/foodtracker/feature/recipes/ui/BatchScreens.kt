package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.feature.recipes.BatchPortionViewModel
import nl.guido.foodtracker.feature.recipes.NewBatchViewModel
import nl.guido.foodtracker.feature.recipes.NewBatchViewModel.Weighing
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Step 1: weigh each ingredient going into the pot. Step 2: weigh the cooked batch. */
@Composable
internal fun NewBatchScreen(
    navController: NavController,
    savedState: SavedStateHandle,
    onSaved: (Id) -> Unit,
    vm: NewBatchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    CameraGrams(savedState, vm::onWeighed)
    LaunchedEffect(state.savedBatchId) { state.savedBatchId?.let(onSaved) }

    Scaffold(
        topBar = { RecipesTopBar(stringResource(R.string.recipes_cook_batch), onBack = { navController.popBackStack() }) },
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

            SectionTitle(stringResource(R.string.recipes_batch_step_in))
            Hint(stringResource(R.string.recipes_batch_step_in_hint))
            state.lines.forEach { line ->
                IngredientLine(
                    line = line,
                    onGrams = { vm.setGrams(line.key, it) },
                    onRemove = { vm.remove(line.key) },
                    onWeigh = cameraWeigh(navController) { vm.startWeighing(Weighing.Line(line.key)) },
                )
            }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipes_add_ingredient))
            }
            if (state.lines.isNotEmpty()) {
                Hint(stringResource(R.string.recipes_batch_raw_total, state.rawTotal.kcal.roundToInt()))
            }

            SectionTitle(stringResource(R.string.recipes_batch_step_cooked))
            Hint(stringResource(R.string.recipes_batch_step_cooked_hint))
            GramsField(
                value = state.cookedText,
                onValueChange = vm::setCooked,
                label = stringResource(R.string.recipes_batch_cooked_weight),
                onWeigh = cameraWeigh(navController) { vm.startWeighing(Weighing.Cooked) },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = vm::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.recipes_batch_save))
            }
        }
    }

    if (picking) {
        FoodPicker(onPick = { vm.add(it); picking = false }, onClose = { picking = false })
    }
}

private val cookedDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** Step 3: weigh my portion from the batch. The other person opens the same batch and does the same. */
@Composable
internal fun BatchPortionScreen(
    navController: NavController,
    savedState: SavedStateHandle,
    onDone: () -> Unit,
    vm: BatchPortionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    CameraGrams(savedState, vm::onWeighed)
    LaunchedEffect(state.done) { if (state.done) onDone() }

    val batch = state.batch
    Scaffold(
        topBar = { RecipesTopBar(stringResource(R.string.recipes_weigh_my_portion), onBack = { navController.popBackStack() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Loading()
            return@Scaffold
        }
        if (batch == null) {
            Hint(stringResource(R.string.recipes_not_found), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(batch.name, style = MaterialTheme.typography.headlineSmall)
            Hint(
                stringResource(
                    R.string.recipes_batch_summary,
                    batch.cookedOn.format(cookedDate),
                    formatGrams(batch.cookedWeightG),
                    batch.total.kcal.roundToInt(),
                ),
            )
            state.gramsLeft?.let { Hint(stringResource(R.string.recipes_batch_left, formatGrams(it))) }
            SectionTitle(stringResource(R.string.recipes_batch_step_portion))
            Hint(stringResource(R.string.recipes_batch_step_portion_hint))
            GramsField(
                value = state.gramsText,
                onValueChange = vm::setGrams,
                label = stringResource(R.string.recipes_my_portion),
                onWeigh = cameraWeigh(navController) { vm.startWeighing() },
                modifier = Modifier.fillMaxWidth(),
            )
            state.nutrients?.let { NutrientsSummary(it) }
            MealChips(state.meal, vm::setMeal)
            Row(
                Modifier.fillMaxWidth().toggleable(state.lastPortion, role = Role.Checkbox, onValueChange = vm::setLastPortion),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.lastPortion, onCheckedChange = null)
                Text(stringResource(R.string.recipes_batch_last_portion), Modifier.padding(start = 8.dp))
            }
            Button(onClick = vm::log, enabled = state.canLog, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.recipes_log_my_portion))
            }
        }
    }
}
