package nl.guido.foodtracker.feature.sync.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.feature.sync.AccountState
import nl.guido.foodtracker.feature.sync.Problem
import nl.guido.foodtracker.feature.sync.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Sign in, household code, backup status and export. Opened via Routes.SIGN_IN. */
@Composable
fun AccountScreen(onClose: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val joining by viewModel.joining.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val openSignIn = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.signInUrl())))
        } catch (e: ActivityNotFoundException) {
            // No browser on the phone: nothing sensible to do; the button stays available.
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.headlineMedium)
        when {
            !state.configured -> Body(stringResource(R.string.sync_not_configured))
            !state.signedIn -> SignedOut(state, onSignIn = openSignIn, onNotNow = { viewModel.notNow(); onClose() })
            else -> SignedIn(
                state = state,
                joining = joining,
                onSyncNow = viewModel::syncNow,
                onSignInAgain = openSignIn,
                onJoin = viewModel::join,
            )
        }
        Section(stringResource(R.string.sync_export_title)) {
            Body(stringResource(R.string.sync_export_body))
            OutlinedButton(onClick = { exportLauncher.launch(viewModel.exportFileName()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_export_button))
            }
            when (export) {
                ExportResult.SAVED -> Body(stringResource(R.string.sync_export_saved))
                ExportResult.FAILED -> Body(stringResource(R.string.sync_export_failed))
                null -> Unit
            }
        }
    }
}

@Composable
private fun SignedOut(state: AccountState, onSignIn: () -> Unit, onNotNow: () -> Unit) {
    Body(stringResource(R.string.sync_sign_in_body))
    Body(stringResource(R.string.sync_private_note))
    if (state.problem == Problem.SIGN_IN_FAILED) Body(stringResource(R.string.sync_sign_in_failed))
    Button(onClick = onSignIn, enabled = !state.signingIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.signingIn) R.string.sync_signing_in else R.string.sync_sign_in_button))
    }
    TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_not_now)) }
}

@Composable
private fun SignedIn(
    state: AccountState,
    joining: Boolean,
    onSyncNow: () -> Unit,
    onSignInAgain: () -> Unit,
    onJoin: (String, (Boolean) -> Unit) -> Unit,
) {
    val account = state.account ?: return
    Body(stringResource(R.string.sync_signed_in_as, account.email ?: account.displayName.orEmpty()))

    Section(stringResource(R.string.sync_backup_title)) {
        val status = when {
            state.syncing -> stringResource(R.string.sync_status_syncing)
            state.problem == Problem.SIGNED_OUT -> stringResource(R.string.sync_status_signed_out)
            state.offline -> stringResource(R.string.sync_status_offline)
            state.lastSyncedAt > 0 -> stringResource(R.string.sync_status_done, formatTime(state.lastSyncedAt))
            else -> stringResource(R.string.sync_status_never)
        }
        Body(status)
        if (state.problem == Problem.SIGNED_OUT) {
            Button(onClick = onSignInAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_sign_in_button)) }
        } else {
            OutlinedButton(onClick = onSyncNow, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_now_button))
            }
        }
    }

    Section(stringResource(R.string.sync_household_title)) {
        Body(
            if (account.memberCount > 1) stringResource(R.string.sync_household_shared, account.memberCount)
            else stringResource(R.string.sync_household_alone),
        )
        Body(stringResource(R.string.sync_private_note))
        Text(stringResource(R.string.sync_household_code_label), style = MaterialTheme.typography.labelLarge)
        Text(account.inviteCode, style = MaterialTheme.typography.headlineSmall)
        Body(stringResource(R.string.sync_household_code_help))

        var code by remember { mutableStateOf("") }
        var joined by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(12); joined = false },
            label = { Text(stringResource(R.string.sync_join_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { onJoin(code) { ok -> joined = ok; if (ok) code = "" } },
            enabled = code.isNotBlank() && !joining,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.sync_join_button)) }
        if (joined) Body(stringResource(R.string.sync_join_done))
        if (state.problem == Problem.CODE_NOT_FOUND) Body(stringResource(R.string.sync_join_not_found))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
