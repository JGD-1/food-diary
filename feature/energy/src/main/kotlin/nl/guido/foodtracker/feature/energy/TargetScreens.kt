package nl.guido.foodtracker.feature.energy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.TargetBreakdown
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** "How is my target calculated": the sum, line by line, and the inputs to change it. */
@Composable
internal fun EnergyTargetScreen(
    onBack: () -> Unit,
    onLogWeighIn: () -> Unit,
    viewModel: EnergyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EnergyScaffold(stringResource(R.string.energy_target_title), onBack) {
        val current = state ?: return@EnergyScaffold
        val details = current.details
        if (details != null) {
            BreakdownCard(details)
            WeightLine(details)
            OutlinedButton(onClick = onLogWeighIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.energy_log_weigh_in))
            }
            SectionTitle(R.string.energy_change_inputs)
        } else {
            Text(stringResource(R.string.energy_profile_intro), style = MaterialTheme.typography.bodyLarge)
        }
        ProfileFormFields(current, viewModel, onSaved = {})
    }
}

/** The profile on its own, plus the way to account, sync and export (stream 7). */
@Composable
internal fun ProfileScreen(
    onBack: () -> Unit,
    onOpenTarget: () -> Unit,
    onAccount: () -> Unit,
    viewModel: EnergyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EnergyScaffold(stringResource(R.string.energy_profile_title), onBack) {
        val current = state ?: return@EnergyScaffold
        if (current.profile == null) {
            Text(stringResource(R.string.energy_profile_intro), style = MaterialTheme.typography.bodyLarge)
        }
        ProfileFormFields(current, viewModel, onSaved = if (current.profile == null) onOpenTarget else ({}))
        HorizontalDivider()
        OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.energy_account_sync))
        }
        SectionTitle(R.string.energy_data_sources)
        Text(
            stringResource(R.string.energy_data_sources_nevo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnergyScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.energy_back))
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
            content()
        }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BreakdownCard(details: EnergyDetails) {
    val b: TargetBreakdown = details.breakdown
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.energy_target_daily), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.energy_kcal, kcal(b.targetKcal)),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (b.isManual) {
                Note(stringResource(R.string.energy_manual_note, kcal(b.targetKcal), kcal(details.calculatedTargetKcal)))
            }
            HorizontalDivider()
            SumLine(
                amount = stringResource(R.string.energy_kcal, kcal(b.bmrKcal)),
                title = stringResource(R.string.energy_line_resting_title),
                body = stringResource(
                    R.string.energy_line_resting_body,
                    details.age,
                    stringResource(details.sexLabel()),
                    formatKg(details.weightKg),
                ),
            )
            SumLine(
                amount = stringResource(R.string.energy_times, String.format("%.3f", b.activityFactor).trimEnd('0')),
                title = stringResource(R.string.energy_line_activity_title, kcal(b.maintenanceKcal)),
                body = stringResource(R.string.energy_line_activity_body),
            )
            when (details.direction) {
                Direction.LOSE -> SumLine(
                    amount = signedKcal(-b.deficitKcal),
                    title = stringResource(R.string.energy_line_lose_title),
                    body = stringResource(R.string.energy_line_lose_body, formatPace(details.paceKg)),
                )
                Direction.GAIN -> SumLine(
                    amount = signedKcal(-b.deficitKcal),
                    title = stringResource(R.string.energy_line_gain_title),
                    body = stringResource(R.string.energy_line_gain_body, formatPace(details.paceKg)),
                )
                Direction.MAINTAIN -> SumLine(
                    amount = signedKcal(0),
                    title = stringResource(R.string.energy_line_maintain_title),
                    body = stringResource(R.string.energy_line_maintain_body),
                )
            }
            SumLine(
                amount = signedKcal(b.adjustmentKcal),
                title = stringResource(R.string.energy_line_adjust_title),
                body = if (details.weighInsUntilLearning > 0) {
                    stringResource(R.string.energy_line_adjust_waiting, details.weighInsUntilLearning)
                } else {
                    stringResource(R.string.energy_line_adjust_body)
                },
            )
            HorizontalDivider()
            SumLine(
                amount = stringResource(R.string.energy_kcal, kcal(details.calculatedTargetKcal)),
                title = stringResource(R.string.energy_line_total_title),
                body = if (details.keptAtMinimum) {
                    stringResource(R.string.energy_line_minimum, kcal(EnergyMath.MIN_TARGET_KCAL))
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SumLine(amount: String, title: String, body: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (body != null) Note(body)
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WeightLine(details: EnergyDetails) {
    val date = details.weightDate
    Note(
        if (date != null) {
            stringResource(R.string.energy_weight_from_weigh_in, formatKg(details.weightKg), shortDate(date))
        } else {
            stringResource(R.string.energy_weight_from_start, formatKg(details.weightKg))
        },
    )
}

/** All profile inputs. Saving recalculates the target straight away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFormFields(state: EnergyState, viewModel: EnergyViewModel, onSaved: () -> Unit) {
    val isNew = state.profile == null
    var form by remember(state.profile) { mutableStateOf(ProfileForm.from(state.profile)) }
    var showCheck by remember { mutableStateOf(false) }
    var saved by remember(state.profile) { mutableStateOf(false) }

    NumberField(form.birthYear, R.string.energy_form_birth_year, decimal = false) { form = form.copy(birthYear = it) }

    SectionTitle(R.string.energy_form_sex)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { sex ->
            FilterChip(
                selected = form.sex == sex,
                onClick = { form = form.copy(sex = sex) },
                label = { Text(stringResource(sex.label())) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }

    NumberField(form.heightCm, R.string.energy_form_height, decimal = false) { form = form.copy(heightCm = it) }
    if (isNew) {
        NumberField(form.weightKg, R.string.energy_form_weight, decimal = true) { form = form.copy(weightKg = it) }
    }

    SectionTitle(R.string.energy_form_activity)
    Column {
        ActivityLevel.entries.forEach { level ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = form.activity == level, role = Role.RadioButton) {
                        form = form.copy(activity = level)
                    },
            ) {
                RadioButton(selected = form.activity == level, onClick = null)
                Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                    Text(stringResource(level.label()), style = MaterialTheme.typography.bodyLarge)
                    Note(stringResource(level.description()))
                }
            }
        }
    }

    NumberField(form.targetWeightKg, R.string.energy_form_target_weight, decimal = true) { form = form.copy(targetWeightKg = it) }

    SectionTitle(R.string.energy_form_pace)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileForm.PACES.forEach { pace ->
            FilterChip(
                selected = form.weeklyPaceKg == pace,
                onClick = { form = form.copy(weeklyPaceKg = pace) },
                label = { Text(stringResource(R.string.energy_pace_option, formatPace(pace))) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = form.useOwnTarget, role = Role.Switch) { form = form.copy(useOwnTarget = it) },
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.energy_form_own_target), style = MaterialTheme.typography.bodyLarge)
            Note(stringResource(R.string.energy_form_own_target_body))
        }
        Switch(checked = form.useOwnTarget, onCheckedChange = null)
    }
    if (form.useOwnTarget) {
        NumberField(form.ownTargetKcal, R.string.energy_form_own_target_kcal, decimal = false) { form = form.copy(ownTargetKcal = it) }
    }

    if (showCheck) Note(stringResource(R.string.energy_form_check))
    if (saved) Note(stringResource(R.string.energy_form_saved))
    Button(
        onClick = {
            val ok = viewModel.saveProfile(form) {
                saved = true
                onSaved()
            }
            showCheck = !ok
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.energy_form_save), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun NumberField(value: String, @StringRes label: Int, decimal: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@StringRes
private fun EnergyDetails.sexLabel(): Int = if (sex == Sex.MALE) R.string.energy_sex_male_lower else R.string.energy_sex_female_lower

@StringRes
private fun Sex.label(): Int = when (this) {
    Sex.FEMALE -> R.string.energy_sex_female
    Sex.MALE -> R.string.energy_sex_male
}

@StringRes
private fun ActivityLevel.label(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.energy_activity_sedentary
    ActivityLevel.LIGHT -> R.string.energy_activity_light
    ActivityLevel.MODERATE -> R.string.energy_activity_moderate
    ActivityLevel.ACTIVE -> R.string.energy_activity_active
    ActivityLevel.VERY_ACTIVE -> R.string.energy_activity_very_active
}

@StringRes
private fun ActivityLevel.description(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.energy_activity_sedentary_body
    ActivityLevel.LIGHT -> R.string.energy_activity_light_body
    ActivityLevel.MODERATE -> R.string.energy_activity_moderate_body
    ActivityLevel.ACTIVE -> R.string.energy_activity_active_body
    ActivityLevel.VERY_ACTIVE -> R.string.energy_activity_very_active_body
}

internal fun kcal(value: Int): String = String.format("%,d", value)

private fun signedKcal(value: Int): String = when {
    value > 0 -> "+ ${kcal(value)} kcal"
    value < 0 -> "− ${kcal(abs(value))} kcal"
    else -> "± 0 kcal"
}

/** 0.5 → "0.5", 0.25 → "0.25". */
internal fun formatPace(kg: Double): String = String.format("%.2f", kg).trimEnd('0').trimEnd('.', ',')

internal fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
