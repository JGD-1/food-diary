package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import nl.guido.foodtracker.feature.recipes.logic.Dish
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.filledIn
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.mealAt
import nl.guido.foodtracker.feature.recipes.logic.parseGrams
import nl.guido.foodtracker.feature.recipes.logic.toIngredients
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Log a recipe: pick a saved variant, add extras just for this time (the saved recipe stays
 * the same), choose how much, and optionally save the extras as a new variant or pin it.
 */
@HiltViewModel
internal class LogRecipeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val diary: DiaryRepository,
    private val favourites: Favourites,
) : ViewModel() {
    /** Where a scale reading should go. */
    sealed interface Weighing {
        data object Portion : Weighing
        data class Extra(val key: String) : Weighing
    }

    data class State(
        val loading: Boolean = true,
        val recipe: Recipe? = null,
        val variants: List<RecipeVariant> = emptyList(),
        val variant: RecipeVariant? = null,
        val extras: List<EditableIngredient> = emptyList(),
        val gramsText: String = "",
        val meal: Meal = Meal.DINNER,
        val pinned: PinnedRecipe? = null,
        val rememberPortion: Boolean = false,
        val weighing: Weighing? = null,
        val done: Boolean = false,
    ) {
        val dish: Dish? get() = recipe?.let { Dish(it, variant, extras.filledIn()) }
        val grams: Double? get() = parseGrams(gramsText)
        val nutrients: Nutrients? get() = grams?.let { g -> dish?.nutrientsFor(g) }
        val canLog: Boolean get() = dish != null && grams != null && extras.toIngredients() != null
        val canSaveVariant: Boolean get() = extras.isNotEmpty() && extras.toIngredients() != null
    }

    private val recipeId: String = checkNotNull(savedState[RecipesRoutes.ARG_RECIPE_ID])
    private val _state = MutableStateFlow(
        State(meal = RecipesRoutes.mealArg(savedState[RecipesRoutes.ARG_MEAL]) ?: mealAt(LocalTime.now())),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recipe = recipes.get(recipeId)
            val userId = session.currentUser.value.userId
            val pin = favourites.pinned(userId).first().firstOrNull { it.recipeId == recipeId }
            _state.update {
                it.copy(
                    loading = false,
                    recipe = recipe,
                    pinned = pin,
                    rememberPortion = pin != null && pin.usualPortion == null,
                    gramsText = recipe?.let { r -> formatGrams(Dish(r).suggestedGrams(pin?.usualPortion)) } ?: "",
                )
            }
        }
        viewModelScope.launch {
            recipes.variants(recipeId).collect { list -> _state.update { it.copy(variants = list) } }
        }
    }

    /** Switching variant keeps the same share of the dish, so the suggested amount follows along. */
    fun chooseVariant(variant: RecipeVariant?) = changeDish { it.copy(variant = variant) }

    fun addExtra(food: Food) = _state.update { it.copy(extras = it.extras + EditableIngredient.of(food)) }

    fun setExtraGrams(key: String, text: String) = changeDish { s ->
        s.copy(extras = s.extras.map { if (it.key == key) it.copy(gramsText = text) else it })
    }

    fun removeExtra(key: String) = changeDish { s -> s.copy(extras = s.extras.filterNot { it.key == key }) }

    fun setGrams(text: String) = _state.update { it.copy(gramsText = text) }
    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }
    fun setRememberPortion(on: Boolean) = _state.update { it.copy(rememberPortion = on) }

    fun startWeighing(target: Weighing) = _state.update { it.copy(weighing = target) }
    fun onWeighed(grams: Double) {
        when (val target = _state.value.weighing) {
            Weighing.Portion -> setGrams(formatGrams(grams))
            is Weighing.Extra -> setExtraGrams(target.key, formatGrams(grams))
            null -> return
        }
        _state.update { it.copy(weighing = null) }
    }

    fun togglePin() {
        val s = _state.value
        val recipe = s.recipe ?: return
        val userId = session.currentUser.value.userId
        viewModelScope.launch {
            if (s.pinned != null) {
                favourites.unpin(userId, recipe.id)
                _state.update { it.copy(pinned = null, rememberPortion = false) }
            } else {
                val usual = s.grams?.let { g -> s.dish?.toUsualPortion(g) }
                favourites.pin(userId, recipe.id, usual)
                _state.update { it.copy(pinned = PinnedRecipe(recipe.id, usual), rememberPortion = false) }
            }
        }
    }

    /** Saves the chosen variant plus this time's extras as a new variant, and switches to it. */
    fun saveAsVariant(name: String) {
        val dish = _state.value.dish ?: return
        if (name.isBlank() || !_state.value.canSaveVariant) return
        val variant = dish.asNewVariant(name.trim())
        viewModelScope.launch {
            recipes.saveVariant(variant)
            _state.update { it.copy(variant = variant, extras = emptyList()) }
        }
    }

    fun log() {
        val s = _state.value
        val dish = s.dish ?: return
        val grams = s.grams ?: return
        if (!s.canLog) return
        val user = session.currentUser.value
        viewModelScope.launch {
            diary.save(dish.logEntry(user.userId, LocalDate.now(), s.meal, grams, Instant.now()))
            if (s.pinned != null && s.rememberPortion) {
                favourites.pin(user.userId, dish.recipe.id, dish.toUsualPortion(grams))
            }
            _state.update { it.copy(done = true) }
        }
    }

    /** Applies a change to the dish while keeping the same share of it in the amount field. */
    private fun changeDish(change: (State) -> State) = _state.update { before ->
        val after = change(before)
        val oldDish = before.dish
        val newDish = after.dish
        val grams = before.grams
        if (oldDish == null || newDish == null || grams == null || oldDish.totalGrams <= 0.0) {
            after
        } else {
            after.copy(gramsText = formatGrams(grams / oldDish.totalGrams * newDish.totalGrams))
        }
    }
}
