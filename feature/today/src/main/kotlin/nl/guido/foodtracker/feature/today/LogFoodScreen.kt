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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import nl.guido.foodtracker.core.ui.Routes
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
                onScan = { open(if (ui.meal == Meal.DRINKS) TodayRoutes.CAMERA_DRINK else Routes.CAMERA, Routes.CAMERA) },
                onChooseEntry = viewModel::choose,
                onQuickAdd = viewModel::quickAdd,
                onChooseFood = viewModel::choose,
                onRecipe = viewModel::openRecipe,
                onBatch = viewModel::openBatch,
                onAllRecipes = { open(Routes.RECIPES) },
            )
            ui.amount?.let { step ->
                AmountSheet(
                    step = step,
                    meal = mealFor(step.pick.isDrink, ui.meal),
                    onDismiss = viewModel::closeAmount,
                    onWeigh = { open(TodayRoutes.CAMERA_SCALE, Routes.CAMERA) },
                    onAdd = viewModel::add,
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
    onRecipe: (Recipe) -> Unit,
    onBatch: (Batch) -> Unit,
    onAllRecipes: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(TodayIcons.Back, contentDescription = stringResource(R.string.today_back))
            }
            Text(
                stringResource(R.string.today_log_food_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.today_done), fontWeight = FontWeight.Bold) }
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
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchOnline() }),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onScan,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Icon(TodayIcons.Scan, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.today_scan), fontWeight = FontWeight.Bold)
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
                    ItemRow(
                        title = stringResource(R.string.today_all_recipes),
                        detail = stringResource(R.string.today_all_recipes_detail),
                        onClick = onAllRecipes,
                    )
                }
            }
            if (ui.pinned.isNotEmpty()) {
                section(R.string.today_section_favourites)
                items(ui.pinned, key = { "pin-" + it.id }) { recipe ->
                    ItemRow(
                        title = recipe.name,
                        detail = recipe.usualPortion?.let { it.label ?: stringResource(R.string.today_amount_grams, formatAmount(it.grams)) },
                        onClick = { onRecipe(recipe) },
                    )
                }
            }
            if (ui.batches.isNotEmpty()) {
                section(R.string.today_section_pot)
                items(ui.batches, key = { "batch-" + it.id }) { batch ->
                    val locale = Locale.forLanguageTag(stringResource(R.string.today_locale))
                    val day = batch.cookedOn.format(DateTimeFormatter.ofPattern("EEEE", locale))
                    ItemRow(
                        title = batch.name,
                        detail = stringResource(R.string.today_cooked_on, day),
                        onClick = { onBatch(batch) },
                    )
                }
            }
            if (ui.recent.isNotEmpty()) {
                section(R.string.today_section_recent)
                items(ui.recent, key = { "recent-" + it.id }) { entry ->
                    val name = entry.displayName
                    val quickLabel = stringResource(R.string.today_quick_add_description, name)
                    ItemRow(
                        title = name,
                        detail = (if (entry.isEstimate) "" else amountText(entry) + " · ") +
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
                            Text(stringResource(R.string.today_quick_add), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (searching) {
                if (ui.found.isNotEmpty()) {
                    section(R.string.today_section_foods)
                    items(ui.found, key = { "found-" + it.id }) { food -> FoodRow(food) { onChooseFood(food) } }
                }
                onlineSection(ui, onSearchOnline, onChooseFood)
                val shown = ui.found + ((ui.online as? OnlineSearch.Found)?.foods ?: emptyList())
                if (shown.any { it.source == FoodOrigin.NEVO }) {
                    item(key = "nevo") {
                        Text(
                            stringResource(R.string.today_nevo_credit), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else if (ui.recent.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.today_empty_hint), fontSize = 15.sp,
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
            stringResource(title), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
}

private fun LazyListScope.onlineSection(ui: LogFoodUiState, onSearchOnline: () -> Unit, onChooseFood: (Food) -> Unit) {
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
            items(online.foods, key = { "online-" + it.id }) { food -> FoodRow(food) { onChooseFood(food) } }
        }
        OnlineSearch.NothingFound -> item(key = "online-none") { Note(R.string.today_online_nothing) }
        OnlineSearch.NotReachable -> item(key = "online-off") { Note(R.string.today_online_unreachable) }
    }
}

@Composable
private fun Note(text: Int) {
    Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    val source = when (food.source) {
        FoodOrigin.NEVO -> stringResource(R.string.today_source_nevo)
        FoodOrigin.OFF -> food.brand ?: stringResource(R.string.today_source_off)
        FoodOrigin.LABEL, FoodOrigin.MANUAL -> stringResource(R.string.today_source_own)
    }
    val per100 = stringResource(
        if (food.isDrink) R.string.today_per_100ml else R.string.today_per_100g, formatKcal(food.per100g.kcal),
    )
    ItemRow(title = food.name, detail = "$source · $per100", onClick = onClick)
}

@Composable
private fun ItemRow(
    title: String,
    detail: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) {
                    Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            trailing?.invoke()
        }
    }
}

/** How much? Type it, tap a usual amount, or read the scale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(
    step: AmountStep,
    meal: Meal,
    onDismiss: () -> Unit,
    onWeigh: () -> Unit,
    onAdd: (Double) -> Unit,
) {
    val pick = step.pick
    var text by remember(step) { mutableStateOf(formatAmount(step.grams)) }
    val grams = parseGrams(text)
    val unit = stringResource(if (pick.isDrink) R.string.today_unit_ml else R.string.today_unit_g)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(pick.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
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
                keyboardActions = KeyboardActions(onDone = { grams?.let(onAdd) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickAmounts(pick.isDrink).forEach { amount ->
                    SuggestionChip(onClick = { text = formatAmount(amount) }, label = { Text("${formatAmount(amount)} $unit") })
                }
            }
            OutlinedButton(onClick = onWeigh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(TodayIcons.Weigh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.today_weigh_on_scale))
            }
            if (grams != null) {
                Text(
                    stringResource(R.string.today_amount_total, formatKcal(pick.per100g.forGrams(grams).kcal)),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = { grams?.let(onAdd) },
                enabled = grams != null,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(addToMeal(meal)), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun quickAmounts(isDrink: Boolean): List<Double> =
    if (isDrink) listOf(150.0, 200.0, 250.0, 330.0, 500.0) else listOf(25.0, 50.0, 100.0, 150.0, 200.0, 250.0)
