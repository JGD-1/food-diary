package nl.guido.foodtracker.feature.food.eatout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.feature.food.R
import nl.guido.foodtracker.feature.food.estimate.EstimateFailure

/** "Eat out": type the dish, get a kcal range, log it as an estimate. Opened via Routes.EAT_OUT. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EatOutScreen(onDone: () -> Unit, viewModel: EatOutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.logged) { if (state.logged) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.food_eat_out_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.food_eat_out_body), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = state.dish,
            onValueChange = viewModel::setDish,
            label = { Text(stringResource(R.string.food_eat_out_dish_label)) },
            placeholder = { Text(stringResource(R.string.food_eat_out_dish_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.estimate() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::estimate,
            enabled = state.dish.isNotBlank() && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.loading) R.string.food_eat_out_estimating else R.string.food_eat_out_estimate))
        }

        state.estimate?.let { EstimateCard(it) }
        state.failure?.let { reason ->
            Text(stringResource(failureText(reason)), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.typedKcal,
                onValueChange = viewModel::setTypedKcal,
                label = { Text(stringResource(R.string.food_eat_out_type_kcal)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val canLog = state.dish.isNotBlank() &&
            (state.estimate != null || (state.failure != null && typedKcal(state.typedKcal) != null))
        if (state.estimate != null || state.failure != null) {
            Text(stringResource(R.string.food_eat_out_meal), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER, Meal.SNACKS).forEach { meal ->
                    FilterChip(
                        selected = state.meal == meal,
                        onClick = { viewModel.setMeal(meal) },
                        label = { Text(stringResource(mealText(meal))) },
                    )
                }
            }
            Button(onClick = viewModel::log, enabled = canLog, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.food_eat_out_log))
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_eat_out_cancel))
        }
    }
}

@Composable
private fun EstimateCard(estimate: Estimate) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.food_eat_out_range, estimate.low, estimate.high),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(stringResource(R.string.food_eat_out_typical, estimate.typical), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.food_eat_out_is_estimate), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun failureText(reason: EstimateFailure.Reason) = when (reason) {
    EstimateFailure.Reason.OFFLINE -> R.string.food_eat_out_offline
    EstimateFailure.Reason.NOT_SET_UP -> R.string.food_eat_out_not_set_up
    EstimateFailure.Reason.FAILED -> R.string.food_eat_out_failed
}

private fun mealText(meal: Meal) = when (meal) {
    Meal.BREAKFAST -> R.string.food_meal_breakfast
    Meal.LUNCH -> R.string.food_meal_lunch
    Meal.DINNER -> R.string.food_meal_dinner
    Meal.SNACKS, Meal.DRINKS -> R.string.food_meal_snacks
}
