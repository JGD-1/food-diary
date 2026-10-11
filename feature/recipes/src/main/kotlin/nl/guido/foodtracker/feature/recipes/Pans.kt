package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Pan
import nl.guido.foodtracker.core.model.newId
import javax.inject.Inject

/** The household's pans with their empty weight (S9), so the cooked pot can be weighed in its pan. */
@HiltViewModel
internal class PansViewModel @Inject constructor(
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val pans: StateFlow<List<Pan>> = session.currentUser
        .flatMapLatest { recipes.pans(it.householdId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, grams: Double) {
        if (name.isBlank() || grams <= 0.0) return
        val pan = Pan(newId(), session.currentUser.value.householdId, name.trim(), grams)
        viewModelScope.launch { recipes.savePan(pan) }
    }

    fun delete(id: Id) {
        viewModelScope.launch { recipes.deletePan(id) }
    }
}
