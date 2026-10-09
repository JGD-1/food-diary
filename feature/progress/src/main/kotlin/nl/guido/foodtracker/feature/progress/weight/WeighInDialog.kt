package nl.guido.foodtracker.feature.progress.weight

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import nl.guido.foodtracker.feature.progress.R

/**
 * STAND-IN weigh-in entry: type today's weight. Stream 4 owns the real weigh-in screen;
 * once it has a route, the "Log weigh-in" button opens that instead and this file goes.
 */
@Composable
internal fun WeighInDialog(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val kg = WeightMath.parseKg(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.progress_weight_log)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.progress_weigh_in_label)) },
                suffix = { Text(stringResource(R.string.progress_kg_unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { kg?.let(onSave) }, enabled = kg != null) {
                Text(stringResource(R.string.progress_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.progress_action_cancel)) }
        },
    )
}
