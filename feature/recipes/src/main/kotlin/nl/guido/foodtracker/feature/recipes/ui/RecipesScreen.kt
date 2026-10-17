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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.ui.CardShadow
import nl.guido.foodtracker.core.ui.FoodTheme
import nl.guido.foodtracker.core.ui.IconTile
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.RecipesViewModel
import nl.guido.foodtracker.feature.recipes.WaitingPlatesViewModel
import nl.guido.foodtracker.feature.recipes.logic.BatchDraft
import nl.guido.foodtracker.feature.recipes.logic.Dish
import nl.guido.foodtracker.feature.recipes.logic.OpenBatch
import nl.guido.foodtracker.feature.recipes.logic.dishGrams
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
    onEditBatch: (Id) -> Unit,
    onFridge: () -> Unit,
    vm: RecipesViewModel = hiltViewModel(),
    waitingVm: WaitingPlatesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val waiting by waitingVm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val finishedText = stringResource(R.string.recipes_batch_finished_done)
    val undoText = stringResource(R.string.recipes_undo)
    val thrownAwayText = stringResource(R.string.recipes_draft_thrown_away)
    Scaffold(
        topBar = {
            RecipesTopBar(stringResource(R.string.recipes_recipes_title), onBack) {
                FridgeButton(waiting.boxesInFridge, onFridge)
            }
        },
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
            items(waiting.plates, key = { "plate-" + it.portion.id }) { plate ->
                WaitingPlateCard(plate, onAdd = { waitingVm.add(plate) }, onNotMine = { waitingVm.notMine(plate) })
            }
            item {
                // A batch of your own: name it, add any ingredients, dish up; no recipe needed.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onNewBatch, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.recipes_cook_batch))
                    }
                    Hint(stringResource(R.string.recipes_cook_batch_hint))
                    OutlinedButton(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.recipes_new_recipe))
                    }
                }
            }
            draft?.takeIf { it.hasContent }?.let { cooking ->
                item(key = "draft") {
                    DraftCard(
                        cooking,
                        onCarryOn = onNewBatch,
                        onThrowAway = {
                            vm.throwAwayDraft()
                            scope.launch {
                                val result = snackbar.showSnackbar(thrownAwayText, undoText, duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) vm.restore(cooking)
                            }
                        },
                    )
                }
            }
            if (state.batches.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recipes_batches_section)) }
                items(state.batches, key = { "batch-" + it.batch.id }) { open ->
                    val id = open.batch.id
                    BatchCard(
                        open,
                        onWeigh = { onBatchPortion(id) },
                        onEdit = { onEditBatch(id) },
                        onFreezer = { vm.setFrozen(open.batch, true) },
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
            if (state.frozen.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recipes_freezer_section)) }
                items(state.frozen, key = { "frozen-" + it.batch.id }) { open ->
                    FrozenRow(open, onTakeOut = { vm.setFrozen(open.batch, false) }, onEdit = { onEditBatch(open.batch.id) })
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

/** The batch being cooked on this phone (B1): carry on with it, or throw it away. */
@Composable
private fun DraftCard(draft: BatchDraft, onCarryOn: () -> Unit, onThrowAway: () -> Unit) {
    // Garden: the batch on the stove gets the warm peach card.
    val peach = FoodTheme.colors.tints.peach
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onCarryOn),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = peach.fill),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.recipes_still_cooking, draft.name.trim().ifEmpty { stringResource(R.string.recipes_still_cooking_unnamed) }),
                style = MaterialTheme.typography.titleMedium,
            )
            Hint(pluralStringResource(R.plurals.recipes_still_cooking_lines, draft.lines.size, draft.lines.size))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCarryOn) { Text(stringResource(R.string.recipes_carry_on)) }
                TextButton(onClick = onThrowAway) { Text(stringResource(R.string.recipes_throw_away)) }
            }
        }
    }
}

@Composable
private fun recipeDetail(recipe: Recipe): String =
    stringResource(R.string.recipes_recipe_detail, recipe.total.kcal.roundToInt(), formatGrams(recipe.dishGrams))

@Composable
private fun RecipeCard(name: String, detail: String, onOpen: () -> Unit, onEdit: () -> Unit) {
    // Each recipe keeps its own pastel, picked from its name.
    val tints = FoodTheme.colors.tints.all
    val tint = tints[Math.floorMod(name.hashCode(), tints.size)]
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = whiteCard(), elevation = cardShadow()) {
        Row(
            Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(BowlIcon, tint)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Hint(detail)
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.recipes_edit)) }
        }
    }
}

private val cookedDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** A batch in the freezer: one short line, with "Take out" to put it back in the pot list. */
@Composable
private fun FrozenRow(open: OpenBatch, onTakeOut: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit), colors = whiteCard()) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(open.batch.name, style = MaterialTheme.typography.titleSmall)
                Hint(
                    stringResource(
                        R.string.recipes_batch_cooked_left,
                        open.batch.cookedOn.format(cookedDate),
                        formatGrams(open.gramsLeft),
                    ),
                )
            }
            TextButton(onClick = onTakeOut) { Text(stringResource(R.string.recipes_freezer_take_out)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchCard(open: OpenBatch, onWeigh: () -> Unit, onEdit: () -> Unit, onFreezer: () -> Unit, onFinished: () -> Unit) {
    val batch = open.batch
    Card(Modifier.fillMaxWidth().clickable(onClick = onWeigh), colors = whiteCard(), elevation = cardShadow()) {
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
                TextButton(onClick = onEdit) { Text(stringResource(R.string.recipes_edit)) }
                TextButton(onClick = onFreezer) { Text(stringResource(R.string.recipes_freezer_put_in)) }
                TextButton(onClick = onFinished) { Text(stringResource(R.string.recipes_batch_finished)) }
                OutlinedButton(onClick = onWeigh) { Text(stringResource(R.string.recipes_weigh_my_portion)) }
            }
        }
    }
}

/** Garden cards are white on the cream background. */
@Composable
private fun whiteCard() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
private fun cardShadow() = CardDefaults.cardElevation(defaultElevation = CardShadow)

/** A bowl with steam, on each recipe's pastel tile (from the design). */
private val BowlIcon: ImageVector = ImageVector.Builder("Bowl", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        pathData = addPathNodes("M3 11h18a9 9 0 0 1-18 0zM8 7c0-2 2-2 2-4M14 7c0-2 2-2 2-4"),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()
