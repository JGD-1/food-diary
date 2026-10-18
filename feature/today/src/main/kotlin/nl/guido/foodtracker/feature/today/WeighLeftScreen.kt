package nl.guido.foodtracker.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.core.ui.ScreenHeader
import java.time.Instant
import javax.inject.Inject

internal data class WeighLeftUiState(
    val item: WeighAfterItem? = null,
    val food: Food? = null,
    /** Grams left over, as typed or read from the scale. */
    val left: String = "",
    val done: Boolean = false,
) {
    val eaten: Double? get() = item?.let { i -> parseLeftGrams(left)?.let { eatenGrams(i.startGrams, it) } }
    /** More left than there was at the start: probably the plate or bowl is on the scale too. */
    val moreThanStart: Boolean get() = item != null && (parseLeftGrams(left) ?: 0.0) > item.startGrams
}

/** "Weigh what's left" (round 7, W2): the grams left over, then the difference goes in the diary. */
@HiltViewModel
internal class WeighLeftViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: WeighAfterStore,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WeighLeftUiState())
    val state: StateFlow<WeighLeftUiState> = _state.asStateFlow()

    init {
        val item = savedState.get<String>(TodayRoutes.ARG_ID)?.let(store::get)
        _state.update { it.copy(item = item, done = item == null) }
        if (item != null) viewModelScope.launch { _state.update { it.copy(food = foods.get(item.foodId)) } }
    }

    fun setLeft(text: String) = _state.update { it.copy(left = text) }

    fun onCameraResult(grams: Double?) {
        if (grams != null) setLeft(formatAmount(grams))
    }

    fun add() {
        val s = _state.value
        val item = s.item ?: return
        val food = s.food ?: return
        val eaten = s.eaten ?: return
        viewModelScope.launch {
            diary.save(cameraEntry(food, eaten, item.userId, item.date, item.meal, Instant.now()))
            store.remove(item.id)
            _state.update { it.copy(done = true) }
        }
    }
}

@Composable
internal fun WeighLeftRoute(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: WeighLeftViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    CameraResults(backStackEntry) { _, grams -> viewModel.onCameraResult(grams) }
    LaunchedEffect(s.done) { if (s.done) navController.popBackStack() }
    val item = s.item ?: return
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = stringResource(R.string.today_weigh_left_title),
            onBack = { navController.popBackStack() },
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.today_weigh_left_start, formatAmount(item.startGrams)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.today_weigh_left_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = s.left,
                onValueChange = viewModel::setLeft,
                label = { Text(stringResource(R.string.today_weigh_left_field)) },
                suffix = { Text(stringResource(R.string.today_unit_g)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { navController.navigateSafely(Routes.CAMERA_SCALE, Routes.CAMERA) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(TodayIcons.Weigh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.today_weigh_on_scale))
            }
            val eaten = s.eaten
            when {
                s.moreThanStart -> Text(
                    stringResource(R.string.today_weigh_left_more, formatAmount(item.startGrams)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                eaten != null -> Text(
                    stringResource(
                        R.string.today_weigh_left_eaten, formatAmount(eaten),
                        formatKcal(s.food?.per100g?.forGrams(eaten)?.kcal ?: 0.0),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Button(
                onClick = viewModel::add,
                enabled = eaten != null && s.food != null,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(addToMeal(item.meal)), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
