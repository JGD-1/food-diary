package nl.guido.foodtracker.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.feature.food.R

/** "Eat out": type the dish, get a range, log it as ≈ estimate. */
@Composable
internal fun EatOutScreen(onDone: () -> Unit, viewModel: EatOutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == EatOutState.Step.Logged) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.food_eat_out_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.dish,
            onValueChange = viewModel::onDishChange,
            label = { Text(stringResource(R.string.food_eat_out_dish_label)) },
            placeholder = { Text(stringResource(R.string.food_eat_out_dish_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.estimate() }),
            modifier = Modifier.fillMaxWidth(),
        )
        MealChips(selected = state.meal, onSelect = viewModel::onMealChange)

        when (val step = state.step) {
            EatOutState.Step.Typing -> Button(
                onClick = viewModel::estimate,
                enabled = state.dish.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.food_eat_out_estimate)) }

            EatOutState.Step.Estimating -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.food_eat_out_estimating), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is EatOutState.Step.Result -> ResultCard(step.estimate, onLog = viewModel::logEstimate)

            is EatOutState.Step.Unavailable -> TypeItYourself(
                reason = step.reason,
                typed = state.typedKcal,
                onTypedChange = viewModel::onTypedKcalChange,
                onLog = viewModel::logTyped,
                onRetry = viewModel::estimate,
            )

            EatOutState.Step.Logged -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealChips(selected: Meal, onSelect: (Meal) -> Unit) {
    val meals = listOf(
        Meal.BREAKFAST to R.string.food_meal_breakfast,
        Meal.LUNCH to R.string.food_meal_lunch,
        Meal.DINNER to R.string.food_meal_dinner,
        Meal.SNACKS to R.string.food_meal_snacks,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        meals.forEach { (meal, label) ->
            FilterChip(selected = meal == selected, onClick = { onSelect(meal) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun ResultCard(estimate: Estimate, onLog: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.food_estimate_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.food_estimate_range, estimate.low, estimate.high),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.food_estimate_typical, estimate.typical),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.food_estimate_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(onClick = onLog, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.food_estimate_log, estimate.typical))
    }
}

@Composable
private fun TypeItYourself(
    reason: EatOutState.Reason,
    typed: String,
    onTypedChange: (String) -> Unit,
    onLog: () -> Unit,
    onRetry: () -> Unit,
) {
    val message = when (reason) {
        EatOutState.Reason.NOT_SET_UP -> R.string.food_estimate_not_set_up
        EatOutState.Reason.OFFLINE -> R.string.food_estimate_offline
        EatOutState.Reason.FAILED -> R.string.food_estimate_failed
    }
    Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = typed,
        onValueChange = onTypedChange,
        label = { Text(stringResource(R.string.food_estimate_type_kcal)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onLog() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onLog, enabled = typed.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.food_estimate_log_typed))
    }
    if (reason != EatOutState.Reason.NOT_SET_UP) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_estimate_retry))
        }
    }
}
