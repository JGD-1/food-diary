package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.feature.recipes.logic.OpenBatch
import nl.guido.foodtracker.feature.recipes.logic.openBatches
import java.time.LocalDate
import javax.inject.Inject

/** The Recipes screen: my pinned recipes, all shared recipes, and batches still in the pot. */
@HiltViewModel
internal class RecipesViewModel @Inject constructor(
    session: SessionRepository,
    private val recipes: RecipeRepository,
    favourites: Favourites,
) : ViewModel() {
    data class Pinned(val recipe: Recipe, val usual: PinnedRecipe)
    data class State(
        val loading: Boolean = true,
        val pinned: List<Pinned> = emptyList(),
        val others: List<Recipe> = emptyList(),
        val batches: List<OpenBatch> = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = session.currentUser.flatMapLatest { user ->
        combine(
            recipes.recipes(user.householdId),
            favourites.pinned(user.userId),
            recipes.batches(user.householdId),
            recipes.householdPortions(user.householdId),
        ) { all, pins, batches, portions ->
            val pinById = pins.associateBy { it.recipeId }
            val pinned = all.mapNotNull { r -> pinById[r.id]?.let { Pinned(r, it) } }.sortedBy { it.recipe.name.lowercase() }
            State(
                loading = false,
                pinned = pinned,
                others = all.filter { it.id !in pinById }.sortedBy { it.name.lowercase() },
                batches = openBatches(batches, portions),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** "Finished": the batch leaves the list for everyone in the household. */
    fun finish(batchId: Id) {
        viewModelScope.launch { recipes.finishBatch(batchId, LocalDate.now()) }
    }

    /** Undo for Finished: puts the batch back on the list. */
    fun unfinish(batchId: Id) {
        viewModelScope.launch { recipes.finishBatch(batchId, null) }
    }
}
