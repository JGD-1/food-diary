package nl.guido.foodtracker.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import kotlin.math.roundToInt

/** What was logged for one meal, with amounts that can be changed right there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealSheet(
    meal: Meal,
    entries: List<LogEntry>,
    onDismiss: () -> Unit,
    onChangeAmount: (LogEntry, Double) -> Unit,
    onRemove: (LogEntry) -> Unit,
    onAddMore: () -> Unit,
    onMove: (Meal) -> Unit,
    /** Null when the day shown is today. */
    onCopyToToday: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(mealName(meal)), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (entries.isEmpty()) {
                Text(stringResource(R.string.today_meal_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                EntryRow(entry, onChangeAmount, onRemove)
            }
            Button(
                onClick = onAddMore,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(addToMeal(meal)), fontWeight = FontWeight.Bold)
            }
            if (entries.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val targets = moveTargets(meal)
                    if (targets.isNotEmpty()) MoveButton(targets, onMove)
                    if (onCopyToToday != null) {
                        OutlinedButton(onClick = onCopyToToday, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.today_copy_to_today))
                        }
                    }
                }
            }
        }
    }
}

/** "Move to…": puts the whole meal under another meal, e.g. a snack that was really lunch. */
@Composable
private fun MoveButton(targets: List<Meal>, onMove: (Meal) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.today_move_to))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            targets.forEach { to ->
                DropdownMenuItem(
                    text = { Text(stringResource(mealName(to))) },
                    onClick = { open = false; onMove(to) },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LogEntry, onChangeAmount: (LogEntry, Double) -> Unit, onRemove: (LogEntry) -> Unit) {
    var editing by rememberSaveable(entry.id) { mutableStateOf(false) }
    var text by rememberSaveable(entry.id, entry.portion.grams) { mutableStateOf(formatAmount(entry.portion.grams)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.today_kcal_unit, kcalText(entry.nutrients.kcal.roundToInt(), entry.isEstimate)),
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
        }
        val what = entry.what
        if (what is Logged.Restaurant) {
            Text(
                stringResource(R.string.today_estimate_range, formatKcal(what.estimate.low), formatKcal(what.estimate.high)),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                val grams = parseGrams(text)
                val save = {
                    if (grams != null) {
                        onChangeAmount(entry, grams)
                        editing = false
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.today_amount_label)) },
                    suffix = { Text(stringResource(if (entry.meal == Meal.DRINKS) R.string.today_unit_ml else R.string.today_unit_g)) },
                    singleLine = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.width(140.dp),
                )
                TextButton(onClick = save, enabled = grams != null) { Text(stringResource(R.string.today_save)) }
            } else {
                val amount = amountText(entry)
                if (canChangeAmount(entry)) {
                    val description = stringResource(R.string.today_change_amount, entry.displayName)
                    TextButton(
                        onClick = { editing = true },
                        modifier = Modifier.semantics { contentDescription = "$description, $amount" },
                    ) { Text(amount) }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onRemove(entry) }) { Text(stringResource(R.string.today_remove)) }
            }
        }
    }
}

/** Protein, carbs and fat in grams and as a share of kcal: nice to know, never the focus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MacroSheet(eaten: Nutrients, onDismiss: () -> Unit) {
    val shares = macroShares(eaten)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.today_macros_title), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            MacroRow(stringResource(R.string.today_protein), eaten.protein, shares?.protein)
            MacroRow(stringResource(R.string.today_carbs), eaten.carbs, shares?.carbs)
            MacroRow(stringResource(R.string.today_fat), eaten.fat, shares?.fat)
            Text(
                stringResource(R.string.today_macros_note),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroRow(name: String, grams: Double, share: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.today_amount_grams, formatKcal(grams)), fontSize = 16.sp)
        if (share != null) {
            Text(
                stringResource(R.string.today_macro_share, share.toString()),
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp).width(96.dp),
            )
        }
    }
}
