package nl.guido.foodtracker.feature.food.eatout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.feature.food.R
import nl.guido.foodtracker.feature.food.dishes.Dish

/** "Eat out": pick a dish from the list (or type kcal yourself) and log it as an estimate. Routes.EAT_OUT. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EatOutScreen(onDone: () -> Unit, viewModel: EatOutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.logged) { if (state.logged) onDone() }
    val picked = state.picked
    val typing = picked == null && state.dish.isNotBlank()

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
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (typing && state.suggestions.isNotEmpty()) Suggestions(state.suggestions, viewModel::pick)
        if (picked != null) {
            Text(stringResource(R.string.food_eat_out_how_much), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortionSize.entries.forEach { size ->
                    FilterChip(
                        selected = state.size == size,
                        onClick = { viewModel.setSize(size) },
                        label = { Text(stringResource(sizeText(size))) },
                    )
                }
            }
            EstimateCard(picked, state.size)
        }
        if (typing) {
            Text(stringResource(R.string.food_eat_out_not_listed), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.typedKcal,
                onValueChange = viewModel::setTypedKcal,
                label = { Text(stringResource(R.string.food_eat_out_type_kcal)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (picked != null || (typing && typedKcal(state.typedKcal) != null)) {
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
            Button(onClick = viewModel::log, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.food_eat_out_log))
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_eat_out_cancel))
        }
    }
}

@Composable
private fun Suggestions(dishes: List<Dish>, onPick: (Dish) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        dishes.forEachIndexed { i, dish ->
            if (i > 0) HorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().clickable { onPick(dish) }.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(dish.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(rangeText(dish.estimate), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EstimateCard(dish: Dish, size: PortionSize) {
    val estimate = dish.estimate.scaled(size)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rangeText(estimate), style = MaterialTheme.typography.headlineSmall)
            if (!dish.own) {
                Text(stringResource(R.string.food_eat_out_typical, estimate.typical), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(if (dish.own) R.string.food_eat_out_own_dish else R.string.food_eat_out_is_estimate),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun rangeText(e: Estimate) =
    if (e.low == e.high) stringResource(R.string.food_eat_out_kcal, e.typical)
    else stringResource(R.string.food_eat_out_range, e.low, e.high)

private fun sizeText(size: PortionSize) = when (size) {
    PortionSize.HALF -> R.string.food_eat_out_size_half
    PortionSize.WHOLE -> R.string.food_eat_out_size_whole
    PortionSize.ONE_AND_HALF -> R.string.food_eat_out_size_one_and_half
}

private fun mealText(meal: Meal) = when (meal) {
    Meal.BREAKFAST -> R.string.food_meal_breakfast
    Meal.LUNCH -> R.string.food_meal_lunch
    Meal.DINNER -> R.string.food_meal_dinner
    Meal.SNACKS, Meal.DRINKS -> R.string.food_meal_snacks
}
