package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.recipes.FoodSearchViewModel
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.RecipesRoutes
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipesTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipes_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
internal fun Loading() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** "540 kcal" plus the quiet macro line under it. */
@Composable
internal fun NutrientsSummary(nutrients: Nutrients, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.recipes_kcal, nutrients.kcal.roundToInt()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Hint(
            stringResource(
                R.string.recipes_macros,
                nutrients.protein.roundToInt(), nutrients.carbs.roundToInt(), nutrients.fat.roundToInt(),
            ),
        )
    }
}

@Composable
internal fun MealChips(selected: Meal, onSelect: (Meal) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER, Meal.SNACKS).forEach { meal ->
            FilterChip(selected = meal == selected, onClick = { onSelect(meal) }, label = { Text(mealName(meal)) })
        }
    }
}

@Composable
internal fun mealName(meal: Meal): String = stringResource(
    when (meal) {
        Meal.BREAKFAST -> R.string.recipes_meal_breakfast
        Meal.LUNCH -> R.string.recipes_meal_lunch
        Meal.DINNER -> R.string.recipes_meal_dinner
        Meal.SNACKS -> R.string.recipes_meal_snacks
        Meal.DRINKS -> R.string.recipes_meal_drinks
    },
)

/** A grams field with a "Weigh" button that opens the camera scale reader, when the app has one. */
@Composable
internal fun GramsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onWeigh: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            suffix = { Text(stringResource(R.string.recipes_grams_unit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        if (onWeigh != null) {
            OutlinedButton(onClick = onWeigh) { Text(stringResource(R.string.recipes_weigh)) }
        }
    }
}

/** One ingredient: its name, grams to type or weigh, and a Remove button. */
@Composable
internal fun IngredientLine(
    line: EditableIngredient,
    onGrams: (String) -> Unit,
    onRemove: () -> Unit,
    onWeigh: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            line.toIngredient()?.let {
                Hint(stringResource(R.string.recipes_kcal, it.nutrients.kcal.roundToInt()))
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.recipes_remove)) }
        }
        GramsField(line.gramsText, onGrams, stringResource(R.string.recipes_grams), onWeigh)
    }
}

/**
 * Opens the camera scale reader if the app has it (stream 2), else null so no Weigh button shows.
 * The reading comes back through [CameraGrams].
 */
internal fun cameraWeigh(navController: NavController, before: () -> Unit): (() -> Unit)? {
    val hasScaleReader = navController.graph.any { it.route?.startsWith("camera?") == true }
    if (!hasScaleReader) return null
    return {
        before()
        navController.navigate(RecipesRoutes.CAMERA_SCALE)
    }
}

/** Hands a scale reading from the camera to [onGrams] once, when coming back to this screen. */
@Composable
internal fun CameraGrams(savedState: SavedStateHandle, onGrams: (Double) -> Unit) {
    val grams by savedState.getStateFlow<Double?>(RecipesRoutes.CAMERA_GRAMS, null).collectAsState()
    LaunchedEffect(grams) {
        grams?.let {
            onGrams(it)
            savedState.remove<Double>(RecipesRoutes.CAMERA_GRAMS)
        }
    }
}

/** Full-screen search to pick a food for a recipe, a batch or an extra. */
@Composable
internal fun FoodPicker(onPick: (Food) -> Unit, onClose: () -> Unit) {
    val vm: FoodSearchViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = { vm.clear(); onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recipes_find_food),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clear(); onClose() }) { Text(stringResource(R.string.recipes_close)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.recipes_find_food_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { vm.search(query) }) { Text(stringResource(R.string.recipes_search)) }
                }
                when {
                    state.searching -> Hint(stringResource(R.string.recipes_searching))
                    state.searchedOnline && state.results.isEmpty() -> Hint(stringResource(R.string.recipes_nothing_found))
                    state.searchedFor != null && state.results.isEmpty() -> Hint(stringResource(R.string.recipes_nothing_on_phone))
                }
                // Online search only once the person asks for it (Open Food Facts rules).
                if (state.searchedFor != null && !state.searchedOnline && !state.searching) {
                    OutlinedButton(onClick = vm::searchOnline, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.recipes_search_online))
                    }
                }
                LazyColumn {
                    items(state.results, key = { it.id }) { food ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.clear(); onPick(food) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(food.name, style = MaterialTheme.typography.bodyLarge)
                            Hint(
                                listOfNotNull(
                                    food.brand,
                                    stringResource(R.string.recipes_kcal_per_100g, food.per100g.kcal.roundToInt()),
                                ).joinToString(" · "),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
