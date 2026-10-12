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
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.NoteIngredient
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.parseGrams
import nl.guido.foodtracker.feature.recipes.logic.parsePieces
import nl.guido.foodtracker.feature.recipes.logic.toIngredients
import javax.inject.Inject

/** Create a new recipe, or change a saved one. Saved recipes are shared with the household. */
@HiltViewModel
internal class RecipeEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val ingredientFoods: IngredientFoods,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val isNew: Boolean = true,
        val name: String = "",
        val lines: List<EditableIngredient> = emptyList(),
        /** "Makes … pieces" (S11); empty = not made in pieces. */
        val piecesText: String = "",
        /** Weight after cooking or baking (S12); empty = portions are grams of the raw ingredients. */
        val cookedText: String = "",
        /** A line key, or [WEIGH_COOKED] for the cooked dish. */
        val weighing: String? = null,
        val done: Boolean = false,
        val deleted: Boolean = false,
    ) {
        val pieces: Int? get() = parsePieces(piecesText)
        val cookedGrams: Double? get() = parseGrams(cookedText)
        val canSave: Boolean
            get() = name.isNotBlank() && lines.isNotEmpty() && lines.toIngredients() != null &&
                (piecesText.isBlank() || pieces != null) && (cookedText.isBlank() || cookedGrams != null)
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
                    lines = ingredientFoods.enrich(recipe.ingredients.map { EditableIngredient.of(it) }),
                    piecesText = recipe.pieces?.toString().orEmpty(),
                    cookedText = recipe.cookedWeightG?.let(::formatGrams).orEmpty(),
                )
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun add(picked: PickedFood) {
        viewModelScope.launch {
            val line = ingredientFoods.line(picked) ?: return@launch
            _state.update { it.copy(lines = it.lines + line) }
        }
    }
    /** Lines checked in "Scan a note" (round 4, H), in the order they were on the note. */
    fun addFromNote(items: List<NoteIngredient>) {
        viewModelScope.launch {
            val lines = items.mapNotNull { ingredientFoods.line(it.foodId, it.grams, it.label) }
            _state.update { it.copy(lines = it.lines + lines) }
        }
    }
    fun setGrams(key: String, text: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.key == key) it.withGramsText(text) else it })
    }
    fun chooseAmount(key: String, choice: Portion) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.key == key) it.withChoice(choice) else it })
    }
    fun setPieces(text: String) = _state.update { it.copy(piecesText = text) }
    fun setCooked(text: String) = _state.update { it.copy(cookedText = text) }
    fun remove(key: String) = _state.update { s -> s.copy(lines = s.lines.filterNot { it.key == key }) }

    /** Remembers which line asked for a scale reading, so the reading lands there. */
    fun startWeighing(key: String) = _state.update { it.copy(weighing = key) }
    fun onWeighed(grams: Double) {
        val key = _state.value.weighing ?: return
        if (key == WEIGH_COOKED) setCooked(formatGrams(grams)) else setGrams(key, formatGrams(grams))
        _state.update { it.copy(weighing = null) }
    }

    fun save() {
        val s = _state.value
        val ingredients = s.lines.toIngredients() ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val recipe = original?.copy(name = s.name.trim(), ingredients = ingredients, pieces = s.pieces, cookedWeightG = s.cookedGrams)
                ?: Recipe(
                    id = newId(),
                    householdId = session.currentUser.value.householdId,
                    name = s.name.trim(),
                    ingredients = ingredients,
                    pieces = s.pieces,
                    cookedWeightG = s.cookedGrams,
                )
            recipes.save(recipe)
            _state.update { it.copy(done = true) }
        }
    }

    companion object {
        const val WEIGH_COOKED = "cooked"
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch {
            recipes.delete(id)
            _state.update { it.copy(done = true, deleted = true) }
        }
    }
}
