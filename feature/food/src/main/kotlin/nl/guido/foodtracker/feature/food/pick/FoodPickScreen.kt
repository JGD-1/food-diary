package nl.guido.foodtracker.feature.food.pick

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.ui.FoodPickPurpose
import nl.guido.foodtracker.core.ui.FoodRow
import nl.guido.foodtracker.core.ui.ScreenHeader
import nl.guido.foodtracker.core.ui.amountText
import nl.guido.foodtracker.core.ui.foodDetail
import nl.guido.foodtracker.core.ui.formatGrams
import nl.guido.foodtracker.feature.food.R
import kotlin.math.roundToInt

/**
 * The shared food picker (S21) on Routes.foodPick(purpose): find a food, choose the amount, and hand
 * back a PickedFood; [onPicked] puts it in the opener's savedStateHandle and goes back.
 */
@Composable
internal fun FoodPickScreen(onPicked: (PickedFood) -> Unit, onBack: () -> Unit, viewModel: FoodPickViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(titleFor(ui.purpose)), onBack = onBack)
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.food_pick_search_label)) },
            placeholder = { Text(stringResource(R.string.food_pick_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchOnline() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val typed = ui.query.trim().length >= 2
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!typed) {
                if (ui.recent.isEmpty()) {
                    item(key = "empty") { Note(stringResource(R.string.food_pick_start)) }
                } else {
                    section(R.string.food_pick_recent)
                    items(ui.recent, key = { "recent-" + it.id }) { food -> PickRow(food, viewModel) }
                }
            } else {
                if (ui.found.isNotEmpty()) {
                    section(R.string.food_pick_on_phone)
                    items(ui.found, key = { "found-" + it.id }) { food -> PickRow(food, viewModel) }
                }
                online(ui, viewModel)
            }
        }
    }
    ui.amount?.let { step ->
        AmountSheet(
            step = step,
            purpose = ui.purpose,
            onDismiss = viewModel::closeAmount,
            onAdd = { picked ->
                viewModel.closeAmount()
                onPicked(picked)
            },
        )
    }
}

@Composable
private fun PickRow(food: Food, viewModel: FoodPickViewModel) {
    FoodRow(
        title = food.name,
        plainLine = viewModel.plainLine(food),
        detail = foodDetail(food),
        onClick = { viewModel.choose(food) },
    )
}

private fun LazyListScope.section(title: Int) {
    item(key = "section-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
}

private fun LazyListScope.online(ui: FoodPickState, viewModel: FoodPickViewModel) {
    when (val online = ui.online) {
        OnlinePick.Idle -> item(key = "online-button") {
            OutlinedButton(onClick = viewModel::searchOnline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.food_pick_search_online, ui.query.trim()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OnlinePick.Searching -> item(key = "online-busy") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.food_pick_searching_online), style = MaterialTheme.typography.bodyMedium)
            }
        }
        is OnlinePick.Found -> {
            section(R.string.food_pick_online)
            items(online.foods, key = { "online-" + it.id }) { food -> PickRow(food, viewModel) }
        }
        OnlinePick.NothingFound -> item(key = "online-none") { Note(stringResource(R.string.food_pick_online_nothing)) }
        OnlinePick.NotReachable -> item(key = "online-off") { Note(stringResource(R.string.food_pick_online_unreachable)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** How much? Type grams (ml for drinks), or tap pieces, a serving, the pack or a usual amount. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(step: PickAmount, purpose: FoodPickPurpose, onDismiss: () -> Unit, onAdd: (PickedFood) -> Unit) {
    val food = step.food
    var text by remember(step) { mutableStateOf("") }
    var chip by remember(step) { mutableStateOf<PickChip?>(null) }
    var chipLabel by remember(step) { mutableStateOf<String?>(null) }
    val grams = parseAmount(text)
    val unit = stringResource(if (food.isDrink) R.string.food_pick_unit_ml else R.string.food_pick_unit_g)
    val serving = stringResource(R.string.food_pick_serving)
    val pack = stringResource(R.string.food_pick_pack)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(food.name, style = MaterialTheme.typography.titleLarge)
                step.plainLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(foodDetail(food), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.food_pick_amount)) },
                suffix = { Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                step.chips.forEach { c ->
                    val label = when (c.kind) {
                        PickChip.Kind.PIECE -> step.pieceWords?.label(c.count)
                        PickChip.Kind.SERVING -> serving
                        PickChip.Kind.PACK -> pack
                    }
                    SuggestionChip(
                        onClick = { text = formatGrams(c.grams); chip = c; chipLabel = label },
                        label = { Text(amountText(label, c.grams, food.isDrink)) },
                    )
                }
                quickAmounts(food.isDrink).forEach { amount ->
                    SuggestionChip(
                        onClick = { text = formatGrams(amount); chip = null; chipLabel = null },
                        label = { Text(amountText(null, amount, food.isDrink)) },
                    )
                }
            }
            if (grams != null) {
                Text(
                    stringResource(R.string.food_pick_kcal, food.per100g.forGrams(grams).kcal.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Button(
                onClick = { grams?.let { onAdd(pickedFood(food, it, chip, chipLabel)) } },
                enabled = grams != null,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(addFor(purpose)), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun titleFor(purpose: FoodPickPurpose): Int = when (purpose) {
    FoodPickPurpose.RECIPE_INGREDIENT, FoodPickPurpose.BATCH_INGREDIENT -> R.string.food_pick_title_ingredient
    FoodPickPurpose.EXTRA -> R.string.food_pick_title_extra
}

private fun addFor(purpose: FoodPickPurpose): Int = when (purpose) {
    FoodPickPurpose.RECIPE_INGREDIENT -> R.string.food_pick_add_recipe
    FoodPickPurpose.BATCH_INGREDIENT -> R.string.food_pick_add_batch
    FoodPickPurpose.EXTRA -> R.string.food_pick_add_extra
}

private fun quickAmounts(isDrink: Boolean): List<Double> =
    if (isDrink) listOf(50.0, 100.0, 150.0, 200.0, 250.0) else listOf(10.0, 25.0, 50.0, 100.0, 150.0, 200.0)
