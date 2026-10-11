package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Pan
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.ui.FoodPickPurpose
import nl.guido.foodtracker.core.ui.FoodPickResult
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.core.ui.amountText
import nl.guido.foodtracker.feature.recipes.PansViewModel
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.RecipesRoutes
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.PIECE_CHOICES
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.parseGrams
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

/** "For Friday 9 October", shown only when logging to a day other than today. */
@Composable
internal fun ForDay(date: LocalDate) {
    if (date != LocalDate.now()) {
        Text(stringResource(R.string.recipes_for_day, date.format(dayFormat)), style = MaterialTheme.typography.titleSmall)
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
    /** Liquids are measured in ml. */
    inMl: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            suffix = { Text(stringResource(if (inMl) R.string.recipes_ml_unit else R.string.recipes_grams_unit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        if (onWeigh != null) {
            OutlinedButton(onClick = onWeigh) { Text(stringResource(R.string.recipes_weigh)) }
        }
    }
}

/**
 * One ingredient: its name, ready-made amounts like Log food ("1 egg", "1 serving", S13),
 * grams (or ml) to type or weigh, and a Remove button. A chosen amount stays on the line ("1 egg ≈ 50 g").
 */
@Composable
internal fun IngredientLine(
    line: EditableIngredient,
    onGrams: (String) -> Unit,
    onRemove: () -> Unit,
    onWeigh: (() -> Unit)?,
    onChoice: (Portion) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge)
                val grams = line.grams
                if (line.amountLabel != null && grams != null) Hint(amountText(line.amountLabel, grams, line.isDrink))
            }
            line.toIngredient()?.let {
                Hint(stringResource(R.string.recipes_kcal, it.nutrients.kcal.roundToInt()))
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.recipes_remove)) }
        }
        if (line.choices.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.choices.forEach { choice ->
                    FilterChip(
                        selected = line.amountLabel != null && line.amountLabel == choice.label && line.grams == choice.grams,
                        onClick = { onChoice(choice) },
                        label = { Text(amountText(choice.label, choice.grams, line.isDrink)) },
                    )
                }
            }
        }
        GramsField(
            line.gramsText, onGrams,
            stringResource(if (line.isDrink) R.string.recipes_amount_ml else R.string.recipes_grams),
            onWeigh, inMl = line.isDrink,
        )
    }
}

/** "½", "1", "2" pieces chips (S11); [label] gives the words for the diary line, e.g. "2 pieces". */
@Composable
internal fun PieceChips(selectedLabel: String?, onPieces: (count: Double, label: String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PIECE_CHOICES.forEach { count ->
            val label = piecesLabel(count)
            FilterChip(selected = selectedLabel == label, onClick = { onPieces(count, label) }, label = { Text(label) })
        }
    }
}

/** "½ piece", "1 piece", "2 pieces". */
@Composable
internal fun piecesLabel(count: Double): String = when (count) {
    0.5 -> stringResource(R.string.recipes_half_piece)
    1.0 -> pluralStringResource(R.plurals.recipes_pieces, 1, 1)
    else -> pluralStringResource(R.plurals.recipes_pieces, count.roundToInt(), count.roundToInt())
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

/** Opens the shared food picker (S21); the choice comes back through [PickedFoods]. */
internal fun openFoodPick(navController: NavController, purpose: FoodPickPurpose) {
    navController.navigate(Routes.foodPick(purpose))
}

/** Hands a food chosen in the shared picker to [onPicked] once, when coming back to this screen. */
@Composable
internal fun PickedFoods(savedState: SavedStateHandle, onPicked: (PickedFood) -> Unit) {
    val text by savedState.getStateFlow<String?>(FoodPickResult.KEY, null).collectAsState()
    LaunchedEffect(text) {
        text?.let {
            savedState.remove<String>(FoodPickResult.KEY)
            FoodPickResult.decode(it)?.let(onPicked)
        }
    }
}

/** Reloads [onChanged] once when coming back from "Change batch" (S10), so the screen shows the new numbers. */
@Composable
internal fun BatchChanged(savedState: SavedStateHandle, onChanged: () -> Unit) {
    val changed by savedState.getStateFlow(RecipesRoutes.BATCH_CHANGED, false).collectAsState()
    LaunchedEffect(changed) {
        if (changed) {
            onChanged()
            savedState[RecipesRoutes.BATCH_CHANGED] = false
        }
    }
}

/** "3", "2.5": pieces left, to half a piece. */
internal fun formatPieces(pieces: Double): String = formatGrams((pieces * 2).roundToInt() / 2.0)

/**
 * Weigh the cooked pot in its pan (S9): pick a saved pan and its empty weight comes off the
 * scale reading. "Pans…" adds or removes the household's pans.
 */
@Composable
internal fun PanChips(selected: Pan?, onSelect: (Pan?) -> Unit) {
    val vm: PansViewModel = hiltViewModel()
    val pans by vm.pans.collectAsState()
    var managing by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.recipes_pan_none)) })
        pans.forEach { pan ->
            FilterChip(
                selected = selected?.id == pan.id,
                onClick = { onSelect(pan) },
                label = { Text(stringResource(R.string.recipes_pan_chip, pan.name, formatGrams(pan.grams))) },
            )
        }
        AssistChip(onClick = { managing = true }, label = { Text(stringResource(R.string.recipes_pans_manage)) })
    }
    if (managing) {
        PansDialog(
            pans,
            onAdd = vm::add,
            onDelete = { id ->
                if (selected?.id == id) onSelect(null)
                vm.delete(id)
            },
            onClose = { managing = false },
        )
    }
}

@Composable
private fun PansDialog(pans: List<Pan>, onAdd: (String, Double) -> Unit, onDelete: (String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var gramsText by rememberSaveable { mutableStateOf("") }
    val grams = parseGrams(gramsText)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.recipes_pans_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(stringResource(R.string.recipes_pans_hint))
                pans.forEach { pan ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.recipes_pan_chip, pan.name, formatGrams(pan.grams)),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDelete(pan.id) }) { Text(stringResource(R.string.recipes_remove)) }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.recipes_pan_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                GramsField(gramsText, { gramsText = it }, stringResource(R.string.recipes_pan_empty_weight), onWeigh = null)
                OutlinedButton(
                    onClick = {
                        grams?.let { onAdd(name, it) }
                        name = ""
                        gramsText = ""
                    },
                    enabled = name.isNotBlank() && grams != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.recipes_pan_add)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.recipes_close)) } },
    )
}
