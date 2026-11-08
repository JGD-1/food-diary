package nl.guido.foodtracker.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The calm start-up card (U1): "Er is een nieuwe versie (0.1.301)" with Bijwerken and Later.
 * The app shows it over its screens; it checks at most once a day and shows nothing when the PC isn't reachable.
 */
@Composable
fun UpdateCard(onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val updater = viewModel.updater
    LaunchedEffect(Unit) { updater.checkIfDue() }
    val state by updater.state.collectAsStateWithLifecycle()
    val dismissed by updater.cardDismissed.collectAsStateWithLifecycle()
    val available = state as? UpdateState.Available ?: return
    if (dismissed) return
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.update_available, available.remote.versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = updater::dismissCard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_later))
                }
                Button(
                    onClick = {
                        updater.dismissCard()
                        onUpdate()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.update_update)) }
            }
        }
    }
}
