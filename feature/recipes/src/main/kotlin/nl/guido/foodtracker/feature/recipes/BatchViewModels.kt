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
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.Pan
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.gramsLeft
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.core.model.sum
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.amountsChanged
import nl.guido.foodtracker.feature.recipes.logic.cookDateAllowed
import nl.guido.foodtracker.feature.recipes.logic.cookedFromReading
import nl.guido.foodtracker.feature.recipes.logic.parsePieces
import nl.guido.foodtracker.feature.recipes.logic.piecesGrams
import nl.guido.foodtracker.feature.recipes.logic.piecesLeft
import nl.guido.foodtracker.feature.recipes.logic.scaledPieces
import nl.guido.foodtracker.feature.recipes.logic.filledIn
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import nl.guido.foodtracker.feature.recipes.logic.logEntry
import nl.guido.foodtracker.feature.recipes.logic.mealAt
import nl.guido.foodtracker.feature.recipes.logic.parseGrams
import nl.guido.foodtracker.feature.recipes.logic.portionFor
import nl.guido.foodtracker.feature.recipes.logic.rescaled
import nl.guido.foodtracker.feature.recipes.logic.toIngredients
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Cooking a batch, step 1 and 2: weigh each ingredient going into the pot, then weigh the
 * whole cooked batch. Starting from a recipe fills in its ingredients; the grams can be
 * changed to what actually went in. The batch is shared, so both people can weigh a portion.
 *
 * With a batch id (Routes.batchEdit) the same form changes a saved batch (S10): portions
 * logged after the change use the new numbers. Delete is offered only while nothing is logged from it.
 */
@HiltViewModel
internal class NewBatchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val ingredientFoods: IngredientFoods,
) : ViewModel() {
    /** Where a scale reading should go: an ingredient line, or the cooked pot. */
    sealed interface Weighing {
        data class Line(val key: String) : Weighing
        data object Cooked : Weighing
    }

    data class State(
        val loading: Boolean = true,
        /** The saved batch being changed; null when cooking a new one. */
        val editing: Batch? = null,
        /** True while someone has logged a portion from the batch being changed: then it can't be deleted. */
        val hasPortions: Boolean = false,
        /** The recipe this batch starts from, if any. */
        val recipe: Recipe? = null,
        val name: String = "",
        val lines: List<EditableIngredient> = emptyList(),
        /** The scale reading for the cooked pot: with the pan when one is picked (S9), else the food alone. */
        val cookedText: String = "",
        /** The pan the pot was weighed in, so its empty weight comes off (S9). */
        val pan: Pan? = null,
        /** "Makes … pieces" (S11); empty = not made in pieces. */
        val piecesText: String = "",
        /** In the freezer (S15). */
        val frozen: Boolean = false,
        /** The day it was cooked: the day being viewed, unless picked otherwise. */
        val cookedOn: LocalDate = LocalDate.now(),
        /** × ½, × 1 or × 2 of the recipe. */
        val scale: Double = 1.0,
        /** Tick to save the amounts that actually went in to the recipe too. */
        val saveToRecipe: Boolean = false,
        val weighing: Weighing? = null,
        /** Set once a new batch is saved: the screen moves on to weighing my portion. */
        val savedBatchId: Id? = null,
        /** Set once a changed batch is saved or deleted: the screen closes. */
        val closed: Boolean = false,
    ) {
        val recipeId: Id? get() = recipe?.id
        /** Offer "Save these amounts to the recipe" only when they differ from it. */
        val canSaveToRecipe: Boolean
            get() = recipe != null && recipeAmounts?.let { amountsChanged(recipe.ingredients, it) } == true
        /** What went in, at the recipe's own size (× 1), for "Save these amounts to the recipe". */
        val recipeAmounts: List<Ingredient>? get() = lines.rescaled(scale, 1.0).toIngredients()
        val rawTotal: Nutrients get() = lines.filledIn().map { it.nutrients }.sum()
        val cookedGrams: Double? get() = cookedFromReading(parseGrams(cookedText), pan)
        val pieces: Int? get() = parsePieces(piecesText)
        val canSave: Boolean
            get() = name.isNotBlank() && lines.isNotEmpty() && lines.toIngredients() != null && cookedGrams != null &&
                (piecesText.isBlank() || pieces != null)
        val canDelete: Boolean get() = editing != null && !hasPortions
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val batchId = savedState.get<String>(RecipesRoutes.ARG_BATCH_ID)?.takeIf { it.isNotEmpty() }
        if (batchId != null) loadForEdit(batchId) else loadNew(savedState)
    }

    private fun loadNew(savedState: SavedStateHandle) {
        val recipeId = savedState.get<String>(RecipesRoutes.ARG_RECIPE_ID)?.takeIf { it.isNotEmpty() }
        val viewedDay = RecipesRoutes.dateArg(savedState[RecipesRoutes.ARG_DATE], LocalDate.now())
        viewModelScope.launch {
            val recipe = recipeId?.let { recipes.get(it) }
            _state.value = State(
                loading = false,
                recipe = recipe,
                cookedOn = viewedDay,
                name = recipe?.name.orEmpty(),
                lines = ingredientFoods.enrich(recipe?.ingredients.orEmpty().map { EditableIngredient.of(it) }),
                piecesText = recipe?.pieces?.toString().orEmpty(),
            )
        }
    }

    private fun loadForEdit(batchId: Id) {
        viewModelScope.launch {
            val batch = recipes.getBatch(batchId)
            val lines = ingredientFoods.enrich(batch?.ingredients.orEmpty().map { EditableIngredient.of(it) })
            _state.update {
                if (batch == null) {
                    it.copy(loading = false)
                } else {
                    it.copy(
                        loading = false,
                        editing = batch,
                        name = batch.name,
                        lines = lines,
                        cookedText = formatGrams(batch.cookedWeightG),
                        cookedOn = batch.cookedOn,
                        piecesText = batch.pieces?.toString().orEmpty(),
                        frozen = batch.frozen,
                    )
                }
            }
        }
        viewModelScope.launch {
            recipes.portions(batchId).collect { list -> _state.update { it.copy(hasPortions = list.isNotEmpty()) } }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun add(picked: PickedFood) {
        viewModelScope.launch {
            val line = ingredientFoods.line(picked) ?: return@launch
            _state.update { it.copy(lines = it.lines + line) }
        }
    }
    fun setGrams(key: String, text: String) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.key == key) it.withGramsText(text) else it })
    }
    fun chooseAmount(key: String, choice: Portion) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.key == key) it.withChoice(choice) else it })
    }
    fun remove(key: String) = _state.update { s -> s.copy(lines = s.lines.filterNot { it.key == key }) }
    fun setCooked(text: String) = _state.update { it.copy(cookedText = text) }
    fun setCookedOn(date: LocalDate) {
        if (cookDateAllowed(date, LocalDate.now())) _state.update { it.copy(cookedOn = date) }
    }
    fun setScale(scale: Double) = _state.update { s ->
        s.copy(
            scale = scale,
            lines = s.lines.rescaled(s.scale, scale),
            piecesText = s.pieces?.let { scaledPieces(it, s.scale, scale).toString() } ?: s.piecesText,
        )
    }
    fun setPan(pan: Pan?) = _state.update { it.copy(pan = pan) }
    fun setPieces(text: String) = _state.update { it.copy(piecesText = text) }
    fun setFrozen(on: Boolean) = _state.update { it.copy(frozen = on) }
    fun setSaveToRecipe(on: Boolean) = _state.update { it.copy(saveToRecipe = on) }

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
        if (!s.canSave) return
        s.editing?.let { old ->
            val changed = old.copy(
                name = s.name.trim(),
                ingredients = ingredients,
                cookedWeightG = cooked,
                cookedOn = s.cookedOn,
                pieces = s.pieces,
                frozen = s.frozen,
            )
            viewModelScope.launch {
                recipes.saveBatch(changed)
                _state.update { it.copy(closed = true) }
            }
            return
        }
        val batch = Batch(
            id = newId(),
            householdId = session.currentUser.value.householdId,
            recipeId = s.recipeId,
            name = s.name.trim(),
            ingredients = ingredients,
            cookedWeightG = cooked,
            cookedOn = s.cookedOn,
            pieces = s.pieces,
        )
        viewModelScope.launch {
            recipes.saveBatch(batch)
            val amounts = s.recipeAmounts
            if (s.saveToRecipe && s.canSaveToRecipe && amounts != null) s.recipe?.let { recipes.save(it.copy(ingredients = amounts)) }
            _state.update { it.copy(savedBatchId = batch.id) }
        }
    }

    /** Only while nothing is logged from it (S10); the batch leaves the list for everyone. */
    fun delete() {
        val s = _state.value
        val batch = s.editing ?: return
        if (!s.canDelete) return
        viewModelScope.launch {
            recipes.deleteBatch(batch.id)
            _state.update { it.copy(closed = true) }
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
        /** "1 piece" when chosen with a pieces chip (S11); null when grams were typed or weighed. */
        val portionLabel: String? = null,
        val meal: Meal = Meal.DINNER,
        /** The day this lands on: the day being viewed on Today, else today. */
        val date: LocalDate = LocalDate.now(),
        val weighing: Boolean = false,
        /** Everyone's portions so far, to show what is left in the pot. */
        val portions: List<BatchPortion> = emptyList(),
        /** Tick "that was the last of it" to mark the batch Finished when logging. */
        val lastPortion: Boolean = false,
        val done: Boolean = false,
    ) {
        val grams: Double? get() = parseGrams(gramsText)
        val nutrients: Nutrients? get() = grams?.let { g -> batch?.shareFor(g) }
        val gramsLeft: Double? get() = batch?.gramsLeft(portions)
        val piecesLeft: Double? get() = gramsLeft?.let { left -> batch?.piecesLeft(left) }
        val canLog: Boolean get() = batch != null && grams != null
    }

    private val batchId: String = checkNotNull(savedState[RecipesRoutes.ARG_BATCH_ID])
    private val _state = MutableStateFlow(
        State(
            meal = RecipesRoutes.mealArg(savedState[RecipesRoutes.ARG_MEAL]) ?: mealAt(LocalTime.now()),
            date = RecipesRoutes.dateArg(savedState[RecipesRoutes.ARG_DATE], LocalDate.now()),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            recipes.portions(batchId).collect { list -> _state.update { it.copy(portions = list) } }
        }
    }

    /** Reads the batch again, e.g. after it was changed (S10), so the portion uses the new numbers. */
    fun reload() {
        viewModelScope.launch {
            val batch = recipes.getBatch(batchId)
            _state.update { it.copy(loading = false, batch = batch) }
        }
    }

    fun setGrams(text: String) = _state.update { if (it.gramsText == text) it else it.copy(gramsText = text, portionLabel = null) }

    /** A pieces chip (S11): [count] pieces of the batch, remembered on the diary line as [label]. */
    fun setPieces(count: Double, label: String) = _state.update { s ->
        val grams = s.batch?.piecesGrams(count) ?: return@update s
        s.copy(gramsText = formatGrams(grams), portionLabel = label)
    }
    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }
    fun setLastPortion(on: Boolean) = _state.update { it.copy(lastPortion = on) }
    fun startWeighing() = _state.update { it.copy(weighing = true) }
    fun onWeighed(grams: Double) {
        if (!_state.value.weighing) return
        _state.update { it.copy(gramsText = formatGrams(grams), portionLabel = null, weighing = false) }
    }

    fun log() {
        val s = _state.value
        val batch = s.batch ?: return
        val grams = s.grams ?: return
        val user = session.currentUser.value
        viewModelScope.launch {
            val entry = batch.logEntry(user.userId, s.date, s.meal, grams, Instant.now(), s.portionLabel)
            diary.save(entry)
            // Grams only, shared with the household so everyone sees what is left; my diary stays mine.
            recipes.savePortion(batch.portionFor(entry, user.householdId))
            if (s.lastPortion) recipes.finishBatch(batch.id, LocalDate.now())
            _state.update { it.copy(done = true) }
        }
    }
}
