package nl.guido.foodtracker.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.guido.foodtracker.core.ui.ScreenHeader
import javax.inject.Inject

@HiltViewModel
internal class UpdateViewModel @Inject constructor(val updater: Updater) : ViewModel()

/** Profile > Updates: the installed version, "Zoek naar update", download + install, and the PC's address. */
@Composable
internal fun UpdateScreen(onBack: () -> Unit, startDownload: Boolean, viewModel: UpdateViewModel = hiltViewModel()) {
    val updater = viewModel.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val server by updater.serverAddress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(updater.canInstall()) }
    LifecycleResumeEffect(Unit) {
        canInstall = updater.canInstall()
        onPauseOrDispose { }
    }
    // Opened from the start-up card's "Bijwerken": start right away.
    LaunchedEffect(startDownload) {
        val now = updater.state.value
        if (startDownload && now is UpdateState.Available) updater.download(now.remote)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { ScreenHeader(title = stringResource(R.string.update_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.update_installed, updater.installedVersionName), style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StateContent(
                        state = state,
                        canInstall = canInstall,
                        onCheck = updater::checkNow,
                        onDownload = updater::download,
                        onAllow = { context.startActivity(updater.installPermissionSettings()) },
                        onInstall = { context.startActivity(updater.installIntent()) },
                    )
                }
            }
            ServerField(server, onSave = updater::setServer)
            Note(stringResource(R.string.update_how_it_works))
        }
    }
}

@Composable
private fun StateContent(
    state: UpdateState,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: (RemoteVersion) -> Unit,
    onAllow: () -> Unit,
    onInstall: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> CheckButton(onCheck)
        UpdateState.Checking -> {
            Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        UpdateState.UpToDate -> {
            Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyLarge)
            CheckButton(onCheck, outlined = true)
        }
        is UpdateState.Available -> {
            Text(stringResource(R.string.update_available, state.remote.versionName), style = MaterialTheme.typography.titleMedium)
            WideButton(stringResource(R.string.update_update)) { onDownload(state.remote) }
        }
        is UpdateState.Downloading -> {
            Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.Ready -> if (canInstall) {
            Text(stringResource(R.string.update_ready, state.remote.versionName), style = MaterialTheme.typography.bodyLarge)
            WideButton(stringResource(R.string.update_install), onInstall)
        } else {
            Text(stringResource(R.string.update_allow_explain), style = MaterialTheme.typography.bodyLarge)
            WideButton(stringResource(R.string.update_allow), onAllow)
        }
        is UpdateState.Failed -> {
            Text(stringResource(problemText(state.problem)), style = MaterialTheme.typography.bodyLarge)
            CheckButton(onCheck, outlined = true, label = R.string.update_try_again)
        }
        is UpdateState.BadDownload -> {
            Text(stringResource(R.string.update_bad_download), style = MaterialTheme.typography.bodyLarge)
            CheckButton(onCheck, outlined = true, label = R.string.update_try_again)
        }
    }
}

@Composable
private fun ServerField(server: ServerAddress, onSave: (ServerAddress) -> Unit) {
    var text by rememberSaveable(server) { mutableStateOf(server.toString()) }
    val parsed = UpdateLogic.parseAddress(text)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.update_server_label)) },
            supportingText = {
                Text(stringResource(if (parsed == null && text.isNotBlank()) R.string.update_server_invalid else R.string.update_server_hint))
            },
            isError = parsed == null && text.isNotBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null && parsed != server,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.update_server_save)) }
        }
    }
}

@Composable
private fun CheckButton(onCheck: () -> Unit, outlined: Boolean = false, label: Int = R.string.update_check) {
    if (outlined) {
        OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
    } else {
        WideButton(stringResource(label), onCheck)
    }
}

@Composable
private fun WideButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text) }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun problemText(problem: UpdateProblem): Int = when (problem) {
    UpdateProblem.NOT_FOUND, UpdateProblem.NOT_REACHABLE -> R.string.update_problem_not_reachable
    UpdateProblem.NOT_HOME_NETWORK -> R.string.update_problem_not_home
    UpdateProblem.NOT_ON_SERVER, UpdateProblem.BAD_VERSION_FILE, UpdateProblem.CHECK_FAILED -> R.string.update_problem_server
}
