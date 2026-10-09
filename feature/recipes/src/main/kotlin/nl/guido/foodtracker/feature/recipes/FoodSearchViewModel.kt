package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
 * Finds foods to put in a recipe or batch: first on the phone (my foods, NEVO, cached
 * products), then online (Open Food Facts) only when "Search online" is tapped, because
 * Open Food Facts doesn't allow search-as-you-type.
 */
@HiltViewModel
internal class FoodSearchViewModel @Inject constructor(
    private val foodSource: FoodSource,
    private val foods: FoodRepository,
) : ViewModel() {
    data class State(
        val searching: Boolean = false,
        /** The words the shown results are for; null before the first search. */
        val searchedFor: String? = null,
        val searchedOnline: Boolean = false,
        val results: List<Food> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun search(text: String) {
        val query = text.trim()
        if (query.length < 2) return
        job?.cancel()
        _state.value = _state.value.copy(searching = true)
        job = viewModelScope.launch {
            val local = orNothing { foods.search(query, 30).first() }
            val found = orNothing { foodSource.search(query) }
            _state.value = State(searchedFor = query, results = (local + found).distinctBy { it.id })
        }
    }

    /** Adds Open Food Facts results for the same words below what was found on the phone. */
    fun searchOnline() {
        val current = _state.value
        val query = current.searchedFor ?: return
        if (current.searchedOnline || current.searching) return
        _state.value = current.copy(searching = true)
        job = viewModelScope.launch {
            val online = orNothing { foodSource.searchOnline(query) }
            _state.value = current.copy(searchedOnline = true, results = (current.results + online).distinctBy { it.id })
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = State()
    }
}

/** A failed lookup (no internet, a bad answer) shows no results instead of crashing; a cancelled one stays cancelled. */
private suspend fun orNothing(lookup: suspend () -> List<Food>): List<Food> =
    try {
        lookup()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
