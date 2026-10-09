package nl.guido.foodtracker.feature.energy

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** The weekly weigh-in: type the weight, see how it compares with last week. */
@Composable
internal fun WeighInScreen(onDone: () -> Unit, viewModel: EnergyViewModel = hiltViewModel()) {
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
private fun WeighInNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
