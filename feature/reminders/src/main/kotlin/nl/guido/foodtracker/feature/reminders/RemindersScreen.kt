package nl.guido.foodtracker.feature.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Meal
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Profile → Reminders: switch the one daily reminder on or off and pick its time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(ReminderNotifier.canNotify(context)) }
    var showPicker by remember { mutableStateOf(false) }
    // Coming back from the phone settings: check again.
    LifecycleResumeEffect(Unit) {
        canNotify = ReminderNotifier.canNotify(context)
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = ReminderNotifier.canNotify(context)
        if (granted) viewModel.setEnabled(true)
    }
    val turnOn = {
        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEnabled(true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reminders_back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Note(stringResource(R.string.reminders_intro))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = settings.enabled,
                                role = Role.Switch,
                                onValueChange = { on -> if (on) turnOn() else viewModel.setEnabled(false) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.reminders_switch), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = settings.enabled, onCheckedChange = null)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(stringResource(R.string.reminders_time), style = MaterialTheme.typography.titleMedium)
                            Text(formatTime(context, settings.time), style = MaterialTheme.typography.headlineSmall)
                        }
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.reminders_change_time))
                        }
                    }
                }
            }
            val meal = mealForTime(settings.time)
            val title = stringResource(notifyTitle(meal))
            Note(stringResource(R.string.reminders_preview, formatTime(context, settings.time), title))
            Note(stringResource(R.string.reminders_skip_note, stringResource(mealName(meal))))
            if (settings.enabled && !canNotify) {
                Note(stringResource(R.string.reminders_blocked))
                OutlinedButton(
                    onClick = { context.startActivity(notificationSettings(context)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.reminders_open_settings))
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = settings.time.hour,
            initialMinute = settings.time.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.reminders_picker_title)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(LocalTime.of(state.hour, state.minute))
                    showPicker = false
                }) { Text(stringResource(R.string.reminders_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.reminders_picker_cancel)) }
            },
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatTime(context: Context, time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@StringRes
internal fun notifyTitle(meal: Meal): Int = when (meal) {
    Meal.BREAKFAST -> R.string.reminders_notify_breakfast
    Meal.LUNCH -> R.string.reminders_notify_lunch
    Meal.DINNER -> R.string.reminders_notify_dinner
    Meal.SNACKS, Meal.DRINKS -> R.string.reminders_notify_snacks
}

@StringRes
private fun mealName(meal: Meal): Int = when (meal) {
    Meal.BREAKFAST -> R.string.reminders_meal_breakfast
    Meal.LUNCH -> R.string.reminders_meal_lunch
    Meal.DINNER -> R.string.reminders_meal_dinner
    Meal.SNACKS, Meal.DRINKS -> R.string.reminders_meal_snacks
}
