package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.toIngredients
import javax.inject.Inject

/** Create a new recipe, or change a saved one. Saved recipes are shared with the household. */
@HiltViewModel
internal class RecipeEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val isNew: Boolean = true,
        val name: String = "",
        val lines: List<EditableIngredient> = emptyList(),
        val weighing: String? = null,
        val done: Boolean = false,
        val deleted: Boolean = false,
    ) {
        val canSave: Boolean get() = name.isNotBlank() && lines.isNotEmpty() && lines.toIngredients() != null
    }

    private val recipeId: String? = savedState.get<String>(RecipesRoutes.ARG_RECIPE_ID)?.takeIf { it.isNotEmpty() }
    private var original: Recipe? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recipe = recipeId?.let { recipes.get(it) }
            original = recipe
            _state.value = if (recipe == null) {
                State(loading = false)
            } else {
                State(
                    loading = false, isNew = false, name = recipe.name,
                    lines = recipe.ingredients.map { EditableIngredient.of(it) },
                )
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun add(food: Food) = _state.update { it.copy(lines = it.lines + EditableIngredient.of(food)) }
    fun setGrams(key: String, text: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.key == key) it.copy(gramsText = text) else it })
    }
    fun remove(key: String) = _state.update { s -> s.copy(lines = s.lines.filterNot { it.key == key }) }

    /** Remembers which line asked for a scale reading, so the reading lands there. */
    fun startWeighing(key: String) = _state.update { it.copy(weighing = key) }
    fun onWeighed(grams: Double) {
        val key = _state.value.weighing ?: return
        setGrams(key, formatGrams(grams))
        _state.update { it.copy(weighing = null) }
    }

    fun save() {
        val s = _state.value
        val ingredients = s.lines.toIngredients() ?: return
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val recipe = original?.copy(name = s.name.trim(), ingredients = ingredients)
                ?: Recipe(
                    id = newId(),
                    householdId = session.currentUser.value.householdId,
                    name = s.name.trim(),
                    ingredients = ingredients,
                )
            recipes.save(recipe)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch {
            recipes.delete(id)
            _state.update { it.copy(done = true, deleted = true) }
        }
    }
}
