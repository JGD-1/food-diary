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
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.core.model.sum
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.filledIn
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.logEntry
import nl.guido.foodtracker.feature.recipes.logic.mealAt
import nl.guido.foodtracker.feature.recipes.logic.parseGrams
import nl.guido.foodtracker.feature.recipes.logic.toIngredients
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Cooking a batch, step 1 and 2: weigh each ingredient going into the pot, then weigh the
 * whole cooked batch. Starting from a recipe fills in its ingredients; the grams can be
 * changed to what actually went in. The batch is shared, so both people can weigh a portion.
 */
@HiltViewModel
internal class NewBatchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
) : ViewModel() {
    /** Where a scale reading should go: an ingredient line, or the cooked pot. */
    sealed interface Weighing {
        data class Line(val key: String) : Weighing
        data object Cooked : Weighing
    }

    data class State(
        val loading: Boolean = true,
        val recipeId: Id? = null,
        val name: String = "",
        val lines: List<EditableIngredient> = emptyList(),
        val cookedText: String = "",
        val weighing: Weighing? = null,
        /** Set once saved: the screen moves on to weighing my portion. */
        val savedBatchId: Id? = null,
    ) {
        val rawTotal: Nutrients get() = lines.filledIn().map { it.nutrients }.sum()
        val cookedGrams: Double? get() = parseGrams(cookedText)
        val canSave: Boolean
            get() = name.isNotBlank() && lines.isNotEmpty() && lines.toIngredients() != null && cookedGrams != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val recipeId = savedState.get<String>(RecipesRoutes.ARG_RECIPE_ID)?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            val recipe = recipeId?.let { recipes.get(it) }
            _state.value = State(
                loading = false,
                recipeId = recipe?.id,
                name = recipe?.name.orEmpty(),
                lines = recipe?.ingredients.orEmpty().map { EditableIngredient.of(it) },
            )
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun add(food: Food) = _state.update { it.copy(lines = it.lines + EditableIngredient.of(food)) }
    fun setGrams(key: String, text: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.key == key) it.copy(gramsText = text) else it })
    }
    fun remove(key: String) = _state.update { s -> s.copy(lines = s.lines.filterNot { it.key == key }) }
    fun setCooked(text: String) = _state.update { it.copy(cookedText = text) }

    fun startWeighing(target: Weighing) = _state.update { it.copy(weighing = target) }
    fun onWeighed(grams: Double) {
        when (val target = _state.value.weighing) {
            is Weighing.Line -> setGrams(target.key, formatGrams(grams))
            Weighing.Cooked -> setCooked(formatGrams(grams))
            null -> return
        }
        _state.update { it.copy(weighing = null) }
    }

    fun save() {
        val s = _state.value
        val ingredients = s.lines.toIngredients() ?: return
        val cooked = s.cookedGrams ?: return
        if (s.name.isBlank()) return
        val batch = Batch(
            id = newId(),
            householdId = session.currentUser.value.householdId,
            recipeId = s.recipeId,
            name = s.name.trim(),
            ingredients = ingredients,
            cookedWeightG = cooked,
            cookedOn = LocalDate.now(),
        )
        viewModelScope.launch {
            recipes.saveBatch(batch)
            _state.update { it.copy(savedBatchId = batch.id) }
        }
    }
}

/** Cooking a batch, step 3 (and for the other person, the only step): weigh my portion and log my share. */
@HiltViewModel
internal class BatchPortionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val diary: DiaryRepository,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val batch: Batch? = null,
        val gramsText: String = "",
        val meal: Meal = Meal.DINNER,
        val weighing: Boolean = false,
        val done: Boolean = false,
    ) {
        val grams: Double? get() = parseGrams(gramsText)
        val nutrients: Nutrients? get() = grams?.let { g -> batch?.shareFor(g) }
        val canLog: Boolean get() = batch != null && grams != null
    }

    private val batchId: String = checkNotNull(savedState[RecipesRoutes.ARG_BATCH_ID])
    private val _state = MutableStateFlow(State(meal = mealAt(LocalTime.now())))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val batch = recipes.getBatch(batchId)
            _state.update { it.copy(loading = false, batch = batch) }
        }
    }

    fun setGrams(text: String) = _state.update { it.copy(gramsText = text) }
    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }
    fun startWeighing() = _state.update { it.copy(weighing = true) }
    fun onWeighed(grams: Double) {
        if (!_state.value.weighing) return
        _state.update { it.copy(gramsText = formatGrams(grams), weighing = false) }
    }

    fun log() {
        val s = _state.value
        val batch = s.batch ?: return
        val grams = s.grams ?: return
        val userId = session.currentUser.value.userId
        viewModelScope.launch {
            diary.save(batch.logEntry(userId, LocalDate.now(), s.meal, grams, Instant.now()))
            _state.update { it.copy(done = true) }
        }
    }
}
