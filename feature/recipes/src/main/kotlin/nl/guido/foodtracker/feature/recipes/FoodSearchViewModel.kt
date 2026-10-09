package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import javax.inject.Inject

/**
 * Finds foods to put in a recipe or batch: foods already on the phone first, then the
 * food sources (Open Food Facts, NEVO). Searches only when asked, never while typing,
 * because Open Food Facts doesn't allow search-as-you-type.
 */
@HiltViewModel
internal class FoodSearchViewModel @Inject constructor(
    private val foodSource: FoodSource,
    private val foods: FoodRepository,
) : ViewModel() {
    data class State(val searching: Boolean = false, val searched: Boolean = false, val results: List<Food> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun search(text: String) {
        val query = text.trim()
        if (query.length < 2) return
        job?.cancel()
        _state.value = _state.value.copy(searching = true)
        job = viewModelScope.launch {
            val local = runCatching { foods.search(query, 30).first() }.getOrDefault(emptyList())
            val found = runCatching { foodSource.search(query) }.getOrDefault(emptyList())
            _state.value = State(searching = false, searched = true, results = (local + found).distinctBy { it.id })
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = State()
    }
}
