package nl.guido.foodtracker.feature.energy

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.WeighIn
import kotlin.math.abs

/**
 * The weekly weigh-in: type the weight, see how it compares with last week.
 * With [weighInId] (tapped on the Weight tab) it changes or deletes that weigh-in instead.
 */
@Composable
internal fun WeighInScreen(weighInId: String?, onDone: () -> Unit, viewModel: EnergyViewModel = hiltViewModel()) {
    if (weighInId != null) {
        ChangeWeighInScreen(weighInId, onDone, viewModel)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var showCheck by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<WeighIn?>(null) }

    EnergyScaffold(stringResource(R.string.energy_weigh_in_title), onDone) {
        val weighIns = state?.weighIns.orEmpty()
        val thisWeek = EnergyMath.weekStart(viewModel.today())
        val result = saved
        if (result == null) {
            val earlierThisWeek = weighIns.filter { EnergyMath.weekStart(it.date) == thisWeek }.maxByOrNull { it.date }
            val last = weighIns.filter { EnergyMath.weekStart(it.date) < thisWeek }.maxByOrNull { it.date }
            WeighInNote(stringResource(R.string.energy_weigh_in_tip))
            if (earlierThisWeek != null) {
                WeighInNote(stringResource(R.string.energy_weigh_in_replaces, formatKg(earlierThisWeek.kg)))
            } else if (last != null) {
                WeighInNote(stringResource(R.string.energy_weigh_in_last, formatKg(last.kg), shortDate(last.date)))
            }
            NumberField(text, R.string.energy_weigh_in_field, decimal = true) { text = it }
            if (showCheck) WeighInNote(stringResource(R.string.energy_weigh_in_check))
            Button(
                onClick = {
                    val kg = parseKg(text)
                    showCheck = kg == null
                    if (kg != null) viewModel.saveWeighIn(kg) { saved = it }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.energy_weigh_in_save), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                stringResource(R.string.energy_weigh_in_saved, formatKg(result.kg)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val previous = EnergyMath.weeklyWeighIns(weighIns).lastOrNull { EnergyMath.weekStart(it.date) < thisWeek }
            val change = previous?.let { result.kg - it.kg }
            WeighInNote(
                when {
                    change == null -> stringResource(R.string.energy_weigh_in_first)
                    abs(change) < 0.05 -> stringResource(R.string.energy_weigh_in_same)
                    change < 0 -> stringResource(R.string.energy_weigh_in_less, formatKg(-change))
                    else -> stringResource(R.string.energy_weigh_in_more, formatKg(change))
                },
            )
            WeighInNote(stringResource(R.string.energy_weigh_in_normal))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.energy_done), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ChangeWeighInScreen(id: String, onDone: () -> Unit, viewModel: EnergyViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var showCheck by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val weighIn = state?.weighIns?.firstOrNull { it.id == id }
    LaunchedEffect(weighIn?.id) {
        if (weighIn != null && text.isEmpty()) text = formatKg(weighIn.kg)
    }

    EnergyScaffold(stringResource(R.string.energy_weigh_in_change_title), onDone) {
        if (state == null) return@EnergyScaffold
        if (weighIn == null) {
            WeighInNote(stringResource(R.string.energy_weigh_in_gone))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.energy_done), style = MaterialTheme.typography.titleMedium)
            }
            return@EnergyScaffold
        }
        WeighInNote(stringResource(R.string.energy_weigh_in_on, shortDate(weighIn.date)))
        NumberField(text, R.string.energy_weigh_in_field, decimal = true) { text = it }
        if (showCheck) WeighInNote(stringResource(R.string.energy_weigh_in_check))
        Button(
            onClick = {
                val kg = parseKg(text)
                showCheck = kg == null
                if (kg != null) viewModel.changeWeighIn(weighIn.id, kg, onDone)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.energy_weigh_in_save), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.energy_weigh_in_delete))
        }
    }

    if (askDelete && weighIn != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(R.string.energy_weigh_in_delete_title)) },
            text = { Text(stringResource(R.string.energy_weigh_in_delete_body, formatKg(weighIn.kg), shortDate(weighIn.date))) },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    viewModel.deleteWeighIn(weighIn.id, onDone)
                }) { Text(stringResource(R.string.energy_weigh_in_delete_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.energy_weigh_in_delete_no)) }
            },
        )
    }
}

@Composable
private fun WeighInNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
