package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.RecipesViewModel
import nl.guido.foodtracker.feature.recipes.logic.Dish
import nl.guido.foodtracker.feature.recipes.logic.OpenBatch
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Batches still in the pot first, then my pinned recipes, then all shared recipes. */
@Composable
internal fun RecipesScreen(
    onBack: () -> Unit,
    onLog: (Id) -> Unit,
    onEdit: (Id?) -> Unit,
    onNewBatch: () -> Unit,
    onBatchPortion: (Id) -> Unit,
    vm: RecipesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val finishedText = stringResource(R.string.recipes_batch_finished_done)
    val undoText = stringResource(R.string.recipes_undo)
    Scaffold(
        topBar = { RecipesTopBar(stringResource(R.string.recipes_recipes_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Loading()
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEdit(null) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.recipes_new_recipe))
                    }
                    OutlinedButton(onClick = onNewBatch, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.recipes_cook_batch))
                    }
                }
            }
            if (state.batches.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recipes_batches_section)) }
                items(state.batches, key = { "batch-" + it.batch.id }) { open ->
                    val id = open.batch.id
                    BatchCard(
                        open,
                        onWeigh = { onBatchPortion(id) },
                        onFinished = {
                            vm.finish(id)
                            scope.launch {
                                val result = snackbar.showSnackbar(finishedText, undoText, duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) vm.unfinish(id)
                            }
                        },
                    )
                }
            }
            if (state.pinned.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recipes_pinned_section)) }
                items(state.pinned, key = { "pin-" + it.recipe.id }) { pinned ->
                    val dish = Dish(pinned.recipe)
                    val usual = pinned.usual.usualPortion
                    val detail = if (usual != null) {
                        val grams = dish.suggestedGrams(usual)
                        stringResource(
                            R.string.recipes_usual_portion,
                            formatGrams(grams),
                            dish.nutrientsFor(grams).kcal.roundToInt(),
                        )
                    } else {
                        recipeDetail(pinned.recipe)
                    }
                    RecipeCard(pinned.recipe.name, detail, onOpen = { onLog(pinned.recipe.id) }, onEdit = { onEdit(pinned.recipe.id) })
                }
            }
            item { SectionTitle(stringResource(R.string.recipes_all_section)) }
            if (state.others.isEmpty() && state.pinned.isEmpty()) {
                item { Hint(stringResource(R.string.recipes_empty)) }
            }
            items(state.others, key = { "recipe-" + it.id }) { recipe ->
                RecipeCard(recipe.name, recipeDetail(recipe), onOpen = { onLog(recipe.id) }, onEdit = { onEdit(recipe.id) })
            }
        }
    }
}

@Composable
private fun recipeDetail(recipe: Recipe): String =
    stringResource(R.string.recipes_recipe_detail, recipe.total.kcal.roundToInt(), formatGrams(recipe.totalGrams))

@Composable
private fun RecipeCard(name: String, detail: String, onOpen: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Hint(detail)
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.recipes_edit)) }
        }
    }
}

private val cookedDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchCard(open: OpenBatch, onWeigh: () -> Unit, onFinished: () -> Unit) {
    val batch = open.batch
    Card(Modifier.fillMaxWidth().clickable(onClick = onWeigh)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 8.dp)) {
            Text(batch.name, style = MaterialTheme.typography.titleMedium)
            Hint(
                stringResource(
                    R.string.recipes_batch_cooked_left,
                    batch.cookedOn.format(cookedDate),
                    formatGrams(open.gramsLeft),
                ),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onFinished) { Text(stringResource(R.string.recipes_batch_finished)) }
                OutlinedButton(onClick = onWeigh) { Text(stringResource(R.string.recipes_weigh_my_portion)) }
            }
        }
    }
}
