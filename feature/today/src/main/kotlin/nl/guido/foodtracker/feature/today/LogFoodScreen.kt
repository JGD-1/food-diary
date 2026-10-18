package nl.guido.foodtracker.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.ui.CardShadow
import nl.guido.foodtracker.core.ui.FoodRow
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.core.ui.ScreenHeader
import nl.guido.foodtracker.core.ui.amountText
import nl.guido.foodtracker.core.ui.foodDetail
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Log food: recent items with one-tap Add, favourites, search, scan, and the amount step. */
@Composable
internal fun LogFoodRoute(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: LogFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val added = stringResource(R.string.today_added)
    val undo = stringResource(R.string.today_undo)
    val comingSoon = stringResource(R.string.today_coming_soon)
    val waitingToWeigh = stringResource(R.string.today_weigh_after_added)

    CameraResults(backStackEntry, viewModel::onCameraResult)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is LogFoodEvent.Added -> launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(added.format(event.name), undo, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.id)
                }
                is LogFoodEvent.Open ->
                    if (!navController.navigateSafely(event.route)) snackbar.showSnackbar(comingSoon)
                is LogFoodEvent.WeighAfter -> launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(waitingToWeigh.format(event.name), undo, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoWeighAfter(event.id)
                }
            }
        }
    }

    fun open(route: String, fallback: String? = null) {
        if (!navController.navigateSafely(route, fallback)) scope.launch { snackbar.showSnackbar(comingSoon) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        state?.let { ui ->
            LogFoodContent(
                ui = ui,
                onBack = { navController.popBackStack() },
                onMeal = viewModel::setMeal,
                onQuery = viewModel::setQuery,
                onSearchOnline = viewModel::searchOnline,
                onScan = { open(if (ui.meal == Meal.DRINKS) Routes.CAMERA_DRINK else Routes.CAMERA, Routes.CAMERA) },
                onChooseEntry = viewModel::choose,
                onQuickAdd = viewModel::quickAdd,
                onChooseFood = viewModel::choose,
                onChoosePin = viewModel::choose,
                onTypeKcal = { open(Routes.eatOut(ui.date, ui.query.trim()), Routes.EAT_OUT) },
                onRecipe = viewModel::openRecipe,
                onBatch = viewModel::openBatch,
                onAllRecipes = { open(Routes.RECIPES) },
                readableName = viewModel::readableName,
            )
            ui.amount?.let { step ->
                AmountSheet(
                    step = step,
                    meal = mealFor(step.pick.isDrink, ui.meal),
                    pin = ui.amountPin,
                    readable = step.pick.food?.let(viewModel::readableName),
                    onDismiss = viewModel::closeAmount,
                    onWeigh = { open(Routes.CAMERA_SCALE, Routes.CAMERA) },
                    onAdd = viewModel::add,
                    onWeighAfter = viewModel::weighAfter,
                    onPin = viewModel::pin,
                    onUnpin = viewModel::unpin,
                    onEditFood = { foodId -> viewModel.closeAmount(); open(Routes.foodEdit(foodId)) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun LogFoodContent(
    ui: LogFoodUiState,
    onBack: () -> Unit,
    onMeal: (Meal) -> Unit,
    onQuery: (String) -> Unit,
    onSearchOnline: () -> Unit,
    onScan: () -> Unit,
    onChooseEntry: (LogEntry) -> Unit,
    onQuickAdd: (LogEntry) -> Unit,
    onChooseFood: (Food) -> Unit,
    onChoosePin: (PinnedFood) -> Unit,
    onTypeKcal: () -> Unit,
    onRecipe: (Recipe) -> Unit,
    onBatch: (Batch) -> Unit,
    onAllRecipes: () -> Unit,
    readableName: (Food) -> String?,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.today_log_food_title),
            onBack = onBack,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.today_done)) }
        }
        ui.date?.let { date ->
            // Logging for an earlier day: say which, so nothing lands on the wrong day by surprise.
            val locale = Locale.forLanguageTag(stringResource(R.string.today_locale))
            val pattern = stringResource(R.string.today_date_pattern)
            val day = date.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
            Text(
                stringResource(R.string.today_log_for_day, day), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Meal.entries.forEach { meal ->
                FilterChip(
                    selected = ui.meal == meal,
                    onClick = { onMeal(meal) },
                    label = { Text(stringResource(mealName(meal))) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.today_search_hint)) },
                leadingIcon = { Icon(TodayIcons.Search, contentDescription = null) },
                singleLine = true,
                // Garden: a white pill with a soft shadow; the green outline shows only while typing.
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchOnline() }),
                modifier = Modifier.weight(1f).shadow(CardShadow, CircleShape),
            )
            FilledTonalButton(
                onClick = onScan,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Icon(TodayIcons.Scan, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.today_scan))
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val searching = ui.query.isNotBlank()
            if (!searching) {
                item(key = "recipes") {
                    FoodRow(
                        title = stringResource(R.string.today_all_recipes),
                        plainLine = null,
                        detail = stringResource(R.string.today_all_recipes_detail),
                        onClick = onAllRecipes,
                    )
                }
                // Eating out lives here now, not as a button on Today: a restaurant meal is one way to log a meal.
                item(key = "eat-out") {
                    FoodRow(
                        title = stringResource(R.string.today_eat_out),
                        plainLine = null,
                        detail = stringResource(R.string.today_eat_out_detail),
                        onClick = onTypeKcal,
                    )
                }
            }
            if (ui.pinned.isNotEmpty() || ui.pinnedFoods.isNotEmpty()) {
                section(R.string.today_section_favourites)
                items(ui.pinned, key = { "pin-" + it.id }) { recipe ->
                    FoodRow(
                        title = recipe.name,
                        plainLine = null,
                        detail = recipe.usualPortion?.let { amountText(it.label, it.grams) },
                        onClick = { onRecipe(recipe) },
                    )
                }
                items(ui.pinnedFoods, key = { "pin-food-" + it.favourite.id }) { pin ->
                    val shown = shownName(pin.food.name, readableName(pin.food))
                    FoodRow(
                        title = shown.title,
                        plainLine = shown.original,
                        detail = pin.usualGrams?.let { amountText(pin.favourite.usualPortion?.label, it, pin.food.isDrink) },
                        onClick = { onChoosePin(pin) },
                    )
                }
            }
            if (ui.batches.isNotEmpty()) {
                section(R.string.today_section_pot)
                items(ui.batches, key = { "batch-" + it.batch.id }) { item -> PotRow(item, onBatch) }
            }
            if (ui.frozen.isNotEmpty()) {
                section(R.string.today_section_freezer)
                items(ui.frozen, key = { "frozen-" + it.batch.id }) { item -> PotRow(item, onBatch) }
            }
            if (ui.recent.isNotEmpty()) {
                section(R.string.today_section_recent)
                items(ui.recent, key = { "recent-" + it.id }) { entry ->
                    val shown = entry.shown(ui.recentReadable)
                    val quickLabel = stringResource(R.string.today_quick_add_description, shown.title)
                    FoodRow(
                        title = shown.title,
                        plainLine = shown.original,
                        detail = (if (entry.isEstimate) "" else entryAmountText(entry) + " · ") +
                            stringResource(R.string.today_kcal_unit, kcalText(entry.nutrients.kcal.roundToInt(), entry.isEstimate)),
                        onClick = { onChooseEntry(entry) },
                    ) {
                        FilledTonalButton(
                            onClick = { onQuickAdd(entry) },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = quickLabel },
                        ) {
                            Icon(TodayIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.today_quick_add), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (searching) {
                if (ui.found.isNotEmpty()) {
                    section(R.string.today_section_foods)
                    items(ui.found, key = { "found-" + it.id }) { food -> FoodItem(food, readableName(food)) { onChooseFood(food) } }
                }
                onlineSection(ui, onSearchOnline) { food -> FoodItem(food, readableName(food)) { onChooseFood(food) } }
                // Not in any list (a market stall, a friend's dish): log it with your own kcal in Eat out.
                item(key = "type-kcal") {
                    FoodRow(
                        title = stringResource(R.string.today_type_kcal),
                        plainLine = null,
                        detail = stringResource(R.string.today_type_kcal_detail, ui.query.trim()),
                        onClick = onTypeKcal,
                    )
                }
                val shown = ui.found + ((ui.online as? OnlineSearch.Found)?.foods ?: emptyList())
                if (shown.any { it.source == FoodOrigin.NEVO }) {
                    item(key = "nevo") {
                        Text(
                            stringResource(nl.guido.foodtracker.core.ui.R.string.nevo_attribution),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else if (ui.recent.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.today_empty_hint), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.section(title: Int) {
    item(key = "section-$title") {
        Text(
            stringResource(title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
}

private fun LazyListScope.onlineSection(ui: LogFoodUiState, onSearchOnline: () -> Unit, foodItem: @Composable (Food) -> Unit) {
    when (val online = ui.online) {
        OnlineSearch.Idle -> item(key = "online-button") {
            OutlinedButton(onClick = onSearchOnline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.today_search_online, ui.query.trim()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OnlineSearch.Searching -> item(key = "online-busy") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.today_searching_online))
            }
        }
        is OnlineSearch.Found -> {
            section(R.string.today_section_online)
            items(online.foods, key = { "online-" + it.id }) { food -> foodItem(food) }
        }
        OnlineSearch.NothingFound -> item(key = "online-none") { Note(R.string.today_online_nothing) }
        OnlineSearch.NotReachable -> item(key = "online-off") { Note(R.string.today_online_unreachable) }
    }
}

@Composable
private fun Note(text: Int) {
    Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

/**
 * A food in a list: the app's readable name, the original NEVO name under it (S1),
 * and "NEVO · 52 kcal per 100 g".
 */
@Composable
private fun FoodItem(food: Food, readable: String?, onClick: () -> Unit) {
    val shown = shownName(food.name, readable)
    FoodRow(title = shown.title, plainLine = shown.original, detail = foodDetail(food), onClick = onClick)
}

/**
 * A batch in "From the pot" or "From the freezer": when it was cooked (the weekday, or the date once
 * it is older than a week, S15) and what is left, in pieces when it was made in pieces (S11).
 */
@Composable
private fun PotRow(item: PotItem, onBatch: (Batch) -> Unit) {
    val locale = Locale.forLanguageTag(stringResource(R.string.today_locale))
    val cookedOn = item.batch.cookedOn
    val pattern = if (showCookDate(cookedOn, LocalDate.now())) stringResource(R.string.today_pot_date_pattern) else "EEEE"
    val day = cookedOn.format(DateTimeFormatter.ofPattern(pattern, locale))
    val pieces = item.piecesLeft?.let { piecesText(it) }
    FoodRow(
        title = item.batch.name,
        plainLine = null,
        detail = stringResource(R.string.today_pot_left, day, amountText(pieces, item.gramsLeft)),
        onClick = { onBatch(item.batch) },
    )
}

/** "1 piece", "2½ pieces". */
@Composable
private fun piecesText(pieces: Double): String =
    LocalContext.current.resources.getQuantityString(R.plurals.today_pieces, if (pieces == 1.0) 1 else 2, formatPieces(pieces))

/** How much? Type it, tap a usual amount, or read the scale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(
    step: AmountStep,
    meal: Meal,
    pin: PinnedFood?,
    readable: String?,
    onDismiss: () -> Unit,
    onWeigh: () -> Unit,
    onAdd: (grams: Double, label: String?) -> Unit,
    onWeighAfter: (grams: Double) -> Unit,
    onPin: (Double) -> Unit,
    onUnpin: () -> Unit,
    onEditFood: (String) -> Unit,
) {
    val pick = step.pick
    var text by remember(step) { mutableStateOf(formatAmount(step.grams)) }
    val grams = parseGrams(text)
    /** The chip tapped last ("1 apple" and its grams); its label is kept while the amount stays the same. */
    var chosen by remember(step) { mutableStateOf<Pair<String, Double>?>(null) }
    val label = chosen?.takeIf { it.second == grams }?.first
    val add = { g: Double -> onAdd(g, label) }
    val unit = stringResource(if (pick.isDrink) R.string.today_unit_ml else R.string.today_unit_g)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                val shown = shownName(pick.name, readable)
                Text(shown.title, style = MaterialTheme.typography.titleLarge)
                // The original NEVO name, unchanged, under the app's readable name (S1).
                shown.original?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(if (pick.isDrink) R.string.today_per_100ml else R.string.today_per_100g, formatKcal(pick.per100g.kcal)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.today_amount_label)) },
                suffix = { Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { grams?.let(add) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pick.chips.forEach { chip ->
                    val name = when (chip.kind) {
                        AmountChip.Kind.SERVING -> stringResource(R.string.today_chip_serving)
                        AmountChip.Kind.PACK -> stringResource(R.string.today_chip_pack)
                        AmountChip.Kind.PIECE -> chip.label.orEmpty()
                    }
                    SuggestionChip(
                        onClick = {
                            text = formatAmount(chip.grams)
                            chosen = parseGrams(text)?.let { name to it }
                        },
                        label = { Text(amountText(name, chip.grams, pick.isDrink)) },
                    )
                }
                quickAmounts(pick.isDrink).forEach { amount ->
                    SuggestionChip(onClick = { text = formatAmount(amount) }, label = { Text(amountText(null, amount, pick.isDrink)) })
                }
            }
            OutlinedButton(onClick = onWeigh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(TodayIcons.Weigh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.today_weigh_on_scale))
            }
            val foodId = pick.foodId
            if (foodId != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pin == null) {
                        TextButton(onClick = { grams?.let(onPin) }, enabled = grams != null) {
                            Text(stringResource(R.string.today_pin))
                        }
                    } else {
                        TextButton(onClick = onUnpin) { Text(stringResource(R.string.today_unpin)) }
                    }
                    if (canEdit(pick.food)) {
                        TextButton(onClick = { onEditFood(foodId) }) { Text(stringResource(R.string.today_edit_food)) }
                    }
                }
            }
            if (grams != null) {
                Text(
                    stringResource(R.string.today_amount_total, formatKcal(pick.per100g.forGrams(grams).kcal)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { grams?.let(add) },
                    enabled = grams != null,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(addToMeal(meal)), style = MaterialTheme.typography.titleMedium)
                }
                // Round 7: weigh it now, eat, weigh what's left; only the difference goes in the diary.
                if (pick.foodId != null && !pick.isDrink) {
                    OutlinedButton(
                        onClick = { grams?.let(onWeighAfter) },
                        enabled = grams != null,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.today_weigh_after), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (pick.foodId != null && !pick.isDrink) {
                Text(
                    stringResource(R.string.today_weigh_after_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun quickAmounts(isDrink: Boolean): List<Double> =
    if (isDrink) listOf(150.0, 200.0, 250.0, 330.0, 500.0) else listOf(25.0, 50.0, 100.0, 150.0, 200.0, 250.0)
