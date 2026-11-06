package nl.guido.foodtracker.feature.sport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.model.SportEntry
import nl.guido.foodtracker.core.ui.ScreenHeader
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the add/change form is open for: nothing, a new line, or an existing one. */
private sealed interface Editing {
    data object New : Editing
    data class Existing(val entry: SportEntry) : Editing
}

@Composable
internal fun SportRoute(onProfile: () -> Unit, viewModel: SportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleted = stringResource(R.string.sport_deleted)
    val undo = stringResource(R.string.sport_undo)
    val link by viewModel.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val allow = rememberLauncherForActivityResult(viewModel.permissionContract) { viewModel.refreshLink() }
    // Each time the tab shows (also after installing Health Connect), check the link and fetch new sport.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshLink() }

    Box(Modifier.fillMaxSize()) {
        SportScreen(
            days = state,
            onAdd = { editing = Editing.New },
            onOpen = { editing = Editing.Existing(it) },
            onProfile = onProfile,
            link = link,
            onLink = { allow.launch(viewModel.permissions) },
            onInstall = { runCatching { context.startActivity(viewModel.installIntent()) } },
            onFetch = viewModel::fetchNow,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    editing?.let { open ->
        val entry = (open as? Editing.Existing)?.entry
        SportDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { form ->
                viewModel.save(form, entry)
                editing = null
            },
            onDelete = entry?.let { e ->
                {
                    editing = null
                    viewModel.delete(e)
                    scope.launch {
                        val result = snackbar.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(e)
                    }
                }
            },
        )
    }
}

@Composable
internal fun SportScreen(
    days: SportDays?,
    onAdd: () -> Unit,
    onOpen: (SportEntry) -> Unit,
    onProfile: () -> Unit,
    link: SportViewModel.Link = SportViewModel.Link(),
    onLink: () -> Unit = {},
    onInstall: () -> Unit = {},
    onFetch: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(title = stringResource(R.string.sport_title), onBack = null, modifier = Modifier.padding(top = 4.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.sport_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (days != null) {
                Card {
                    Title(stringResource(R.string.sport_week_title))
                    Text(summaryText(days.week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Card {
                    Title(stringResource(R.string.sport_today_title))
                    if (days.today.isEmpty()) {
                        Note(stringResource(R.string.sport_today_empty))
                    } else {
                        Lines(days.today, showDate = false, onOpen = onOpen)
                        Note(stringResource(R.string.sport_today_note, kcal(days.todaySummary.kcal)))
                    }
                }
                if (days.earlier.isNotEmpty()) {
                    Card {
                        Title(stringResource(R.string.sport_earlier_title))
                        Lines(days.earlier, showDate = true, onOpen = onOpen)
                    }
                }
            }
            LinkCard(link, onLink, onInstall, onFetch)
            // Sport is typed in separately, so the activity level in the profile shouldn't count it again.
            Card {
                Note(stringResource(R.string.sport_activity_note))
                TextButton(onClick = onProfile, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sport_activity_button))
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.sport_add), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Other apps (Garmin, …) through Health Connect: link once, then new sessions come in by themselves. */
@Composable
private fun LinkCard(link: SportViewModel.Link, onLink: () -> Unit, onInstall: () -> Unit, onFetch: () -> Unit) {
    val state = link.state ?: return
    if (state == LinkState.UNAVAILABLE) return
    Card {
        Title(stringResource(R.string.sport_link_title))
        when (state) {
            LinkState.NEEDS_INSTALL -> {
                Note(stringResource(R.string.sport_link_install_body))
                TextButton(onClick = onInstall, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sport_link_install)) }
            }
            LinkState.NOT_LINKED -> {
                Note(stringResource(R.string.sport_link_body))
                TextButton(onClick = onLink, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sport_link_button)) }
            }
            LinkState.LINKED -> {
                Note(
                    when {
                        link.busy -> stringResource(R.string.sport_link_busy)
                        link.failed -> stringResource(R.string.sport_link_failed)
                        link.lastAdded != null && link.lastAdded > 0 ->
                            pluralStringResource(R.plurals.sport_link_added, link.lastAdded, link.lastAdded)
                        else -> stringResource(R.string.sport_link_linked)
                    },
                )
                TextButton(onClick = onFetch, enabled = !link.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sport_link_fetch))
                }
            }
            LinkState.UNAVAILABLE -> Unit
        }
    }
}

@Composable
private fun Lines(entries: List<SportEntry>, showDate: Boolean, onOpen: (SportEntry) -> Unit) {
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val change = stringResource(R.string.sport_change)
    entries.forEachIndexed { i, entry ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = change) { onOpen(entry) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                val details = listOfNotNull(
                    if (showDate) entry.date.format(dateFormat) else null,
                    entry.minutes?.let { durationText(it) },
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.sport_kcal, kcal(entry.kcal)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun summaryText(s: SportSummary): String {
    if (s.times == 0) return stringResource(R.string.sport_week_empty)
    val parts = buildList {
        add(pluralStringResource(R.plurals.sport_times, s.times, s.times))
        if (s.minutes > 0) add(durationText(s.minutes))
        add(stringResource(R.string.sport_kcal, kcal(s.kcal)))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun durationText(minutes: Int): String {
    val (h, m) = hoursAndMinutes(minutes)
    return when {
        h == 0 -> stringResource(R.string.sport_minutes, m)
        m == 0 -> stringResource(R.string.sport_hours, h)
        else -> stringResource(R.string.sport_hours_minutes, h, m)
    }
}

private fun kcal(value: Int): String = NumberFormat.getIntegerInstance().format(value)

@Composable
private fun SportDialog(entry: SportEntry?, onDismiss: () -> Unit, onSave: (SportForm) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(entry?.name.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(entry?.minutes?.toString().orEmpty()) }
    var kcalText by rememberSaveable { mutableStateOf(entry?.kcal?.toString().orEmpty()) }
    val form = SportForm(name, minutes, kcalText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entry == null) R.string.sport_add else R.string.sport_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.sport_field_name)) },
                    placeholder = { Text(stringResource(R.string.sport_field_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.sport_field_minutes)) },
                    singleLine = true,
                    isError = !form.minutesOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.sport_field_kcal)) },
                    supportingText = if (form.kcalTyped && form.kcalValue == null) {
                        { Text(stringResource(R.string.sport_field_kcal_range, SportEntry.MAX_KCAL)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.sport_delete))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(form) }, enabled = form.canSave) { Text(stringResource(R.string.sport_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sport_cancel)) }
        },
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
