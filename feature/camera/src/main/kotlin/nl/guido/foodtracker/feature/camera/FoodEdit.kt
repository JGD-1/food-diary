package nl.guido.foodtracker.feature.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.ui.Routes
import javax.inject.Inject

/** "Edit food": loading, the form, a food that can't be changed (NEVO, or gone), or saved. */
internal sealed interface FoodEditState {
    data object Loading : FoodEditState
    data class Editing(val original: Food, val draft: FoodDraft) : FoodEditState
    data object CannotEdit : FoodEditState
    data object Saved : FoodEditState
}

/** Routes.FOOD_EDIT ("food-edit/{foodId}"): change a food's name, brand and per 100 g values. */
@HiltViewModel
internal class FoodEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<FoodEditState>(FoodEditState.Loading)
    val state: StateFlow<FoodEditState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<String>(Routes.ARG_FOOD_ID)
        viewModelScope.launch {
            val food = id?.let { foods.get(it) }
            _state.value = if (food != null && food.canEdit()) FoodEditState.Editing(food, FoodDraft.fromFood(food))
            else FoodEditState.CannotEdit
        }
    }

    fun onDraftChange(draft: FoodDraft) =
        _state.update { if (it is FoodEditState.Editing) it.copy(draft = draft) else it }

    fun save() {
        val s = _state.value as? FoodEditState.Editing ?: return
        val updated = s.draft.applyTo(s.original) ?: return
        _state.value = FoodEditState.Saved
        viewModelScope.launch { foods.save(updated) }
    }
}

@Composable
internal fun FoodEditScreen(onDone: () -> Unit, vm: FoodEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == FoodEditState.Saved) onDone() }
    BackHandler(onBack = onDone)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onDone) { Text(stringResource(R.string.camera_back)) }
        Text(stringResource(R.string.camera_edit_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        when (val s = state) {
            FoodEditState.Loading, FoodEditState.Saved -> Unit
            FoodEditState.CannotEdit -> Text(
                stringResource(R.string.camera_edit_cannot),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is FoodEditState.Editing -> {
                Text(
                    stringResource(R.string.camera_edit_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FoodFields(s.draft, vm::onDraftChange)
                Button(
                    onClick = vm::save,
                    enabled = s.draft.canSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.camera_edit_save)) }
            }
        }
    }
}
