package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.feature.recipes.NewBatchViewModel
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.logic.CookStep
import nl.guido.foodtracker.feature.recipes.logic.DishKind
import nl.guido.foodtracker.feature.recipes.logic.formatGrams

/**
 * Round 16: the step bar at the top of Koken. "1 Erin" and "2 Opscheppen" can be tapped to go
 * back and forth; "3 Klaar" is reached by finishing dish up.
 */
@Composable
internal fun CookStepBar(step: CookStep, canDishUp: Boolean, onIn: () -> Unit, onDishUp: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepPill(stringResource(R.string.recipes_step_in), step, CookStep.IN, enabled = step != CookStep.DONE, onClick = onIn, modifier = Modifier.weight(1f))
        StepPill(stringResource(R.string.recipes_step_dish_up), step, CookStep.DISH_UP, enabled = step == CookStep.IN && canDishUp, onClick = onDishUp, modifier = Modifier.weight(1f))
        StepPill(stringResource(R.string.recipes_step_done), step, CookStep.DONE, enabled = false, onClick = {}, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepPill(label: String, current: CookStep, mine: CookStep, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val on = current == mine
    val done = current.ordinal > mine.ordinal
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled && !on,
        shape = RoundedCornerShape(50),
        color = when {
            on -> colors.primary
            done -> colors.secondaryContainer
            else -> colors.surface
        },
        contentColor = when {
            on -> colors.onPrimary
            done -> colors.onSecondaryContainer
            else -> colors.onSurfaceVariant
        },
        modifier = modifier.semantics { selected = on; role = Role.Tab },
    ) {
        Text(
            label,
            // Round 17 check: the step names stop growing at very large font, so the bar stays one line.
            style = capped(MaterialTheme.typography.labelLarge),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/** "+ Toevoegen": one button for everything that goes in the pot (Round 16). */
@Composable
internal fun AddToPotButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.recipes_add), fontWeight = FontWeight.Bold)
    }
}

/**
 * The four ways to add something (Round 16): scan (barcode, or fruit and vegetables), search,
 * a handwritten note, or everything from a saved recipe. Scan is first and biggest.
 * A null action hides that choice (no camera in this build).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPotSheet(
    onScan: (() -> Unit)?,
    onSearch: () -> Unit,
    onNote: () -> Unit,
    recipes: List<Recipe>,
    onRecipe: (Recipe) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingRecipe by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onScan != null) {
                Button(onClick = { onDismiss(); onScan() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    SheetChoice(stringResource(R.string.recipes_add_scan), stringResource(R.string.recipes_add_scan_hint))
                }
            }
            FilledTonalButton(onClick = { onDismiss(); onSearch() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                SheetChoice(stringResource(R.string.recipes_add_search), stringResource(R.string.recipes_add_search_hint))
            }
            FilledTonalButton(onClick = { onDismiss(); onNote() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                SheetChoice(stringResource(R.string.recipes_note_scan), stringResource(R.string.recipes_add_note_hint))
            }
            FilledTonalButton(onClick = { pickingRecipe = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                SheetChoice(stringResource(R.string.recipes_add_recipe), stringResource(R.string.recipes_add_recipe_hint))
            }
            Hint(stringResource(R.string.recipes_add_grams_hint))
        }
    }
    if (pickingRecipe) {
        AlertDialog(
            onDismissRequest = { pickingRecipe = false },
            title = { Text(stringResource(R.string.recipes_add_recipe)) },
            text = {
                if (recipes.isEmpty()) {
                    Hint(stringResource(R.string.recipes_add_recipe_none))
                } else {
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        // P16: each recipe with what it adds, so the right one is easy to pick.
                        recipes.forEach { recipe ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(role = Role.Button) { pickingRecipe = false; onDismiss(); onRecipe(recipe) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    pluralStringResource(
                                        R.plurals.recipes_pick_detail,
                                        recipe.ingredients.size,
                                        recipe.ingredients.size,
                                        formatGrams(recipe.totalGrams),
                                        recipe.total.kcal.roundToInt(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingRecipe = false }) { Text(stringResource(R.string.recipes_cancel)) } },
        )
    }
}

@Composable
private fun SheetChoice(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Step 3, Klaar (Round 16): what happened to every plate and box, "Bewaar als recept" for a cook
 * that didn't start from one, and a button to close the screen.
 */
@Composable
internal fun CookDoneContent(state: NewBatchViewModel.State, onSaveAsRecipe: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.recipes_done_title, state.name.trim()),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                var box = 0
                state.done.forEachIndexed { i, line ->
                    if (i > 0) HorizontalDivider()
                    val text = when {
                        line.kind == DishKind.PLATE && line.forUserId == null ->
                            stringResource(R.string.recipes_done_mine, mealName(state.meal))
                        line.kind == DishKind.PLATE ->
                            stringResource(R.string.recipes_done_theirs, state.nameOf(line.forUserId) ?: stringResource(R.string.recipes_dish_partner))
                        line.kind == DishKind.GUEST -> stringResource(R.string.recipes_dish_guest_plate)
                        line.kind == DishKind.FREEZER -> stringResource(R.string.recipes_done_freezer, ++box)
                        else -> stringResource(R.string.recipes_done_fridge, ++box)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.recipes_done_amount, formatGrams(line.grams), line.kcal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (state.canSaveAsRecipe) {
            OutlinedButton(onClick = onSaveAsRecipe, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recipes_done_save_recipe))
            }
        } else if (state.savedAsRecipe) {
            Hint(stringResource(R.string.recipes_done_saved_recipe))
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.recipes_done_close))
        }
    }
}
