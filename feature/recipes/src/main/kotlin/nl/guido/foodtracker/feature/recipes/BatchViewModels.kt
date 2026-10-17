package nl.guido.foodtracker.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchContainer
import nl.guido.foodtracker.core.model.HouseholdMember
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
import nl.guido.foodtracker.feature.recipes.logic.BatchDraft
import nl.guido.foodtracker.feature.recipes.logic.DishKind
import nl.guido.foodtracker.feature.recipes.logic.DishRow
import nl.guido.foodtracker.feature.recipes.logic.DraftLine
import nl.guido.foodtracker.feature.recipes.logic.dishOutcome
import nl.guido.foodtracker.feature.recipes.logic.dishedTotal
import nl.guido.foodtracker.feature.recipes.logic.startingRows
import nl.guido.foodtracker.feature.recipes.logic.EditableIngredient
import nl.guido.foodtracker.feature.recipes.logic.NoteIngredient
import nl.guido.foodtracker.feature.recipes.logic.ingredientFromPot
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
 *
 * A new batch is kept on the phone as it is typed (B1, [BatchDraftStore]), so leaving the screen
 * or the app closing loses nothing; opening Cook a batch again carries on with it.
 *
 * Round 6 (D1): instead of weighing the hot pot, "Dish up" weighs every plate and box as it is
 * filled. Their sum is the whole batch; my plate goes in my diary, other people's plates wait for
 * them, boxes go to the fridge or freezer. Weighing the pot stays possible ("Weigh the pot instead").
 */
@HiltViewModel
internal class NewBatchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val ingredientFoods: IngredientFoods,
    private val drafts: BatchDraftStore,
    private val diary: DiaryRepository,
) : ViewModel() {
    /** Where a scale reading should go: an ingredient line, the empty pot (B2), the cooked pot, or a plate or box (D1). */
    sealed interface Weighing {
        data class Line(val key: String) : Weighing
        data object EmptyPot : Weighing
        data object Cooked : Weighing
        data class Dish(val key: String) : Weighing
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
        /** "The pot stays on the scale" (B2): each reading is the whole pot, the new ingredient is the difference. */
        val potOnScale: Boolean = false,
        /** The empty pot's weight; empty or 0 when the scale was set to zero with the pot on it. */
        val emptyPotText: String = "",
        /** The last pot reading wasn't more than what was already in the pot, so nothing was filled in. */
        val potReadingTooLow: Boolean = false,
        /** True when this screen carries on with a batch kept from before (B1). */
        val resumed: Boolean = false,
        val weighing: Weighing? = null,
        /** Set once a new batch is saved: the screen moves on to weighing my portion. */
        val savedBatchId: Id? = null,
        /** Set once a changed batch is saved or deleted: the screen closes. */
        val closed: Boolean = false,
        /** Dishing up (D1): the plates and boxes being filled. */
        val dishingUp: Boolean = false,
        val dishRows: List<DishRow> = emptyList(),
        val restText: String = "",
        /** Everyone in the household, for "<name>'s plate". */
        val members: List<HouseholdMember> = emptyList(),
        val myUserId: Id = "",
        /** The meal my plate lands in. */
        val meal: Meal = mealAt(LocalTime.now()),
        /** Set once "That's everything" has saved it all: the screen closes. */
        val dishedUp: Boolean = false,
    ) {
        val recipeId: Id? get() = recipe?.id
        /** Offer "Save these amounts to the recipe" only when they differ from it. */
        val canSaveToRecipe: Boolean
            get() = recipe != null && recipeAmounts?.let { amountsChanged(recipe.ingredients, it) } == true
        /** What went in, at the recipe's own size (× 1), for "Save these amounts to the recipe". */
        val recipeAmounts: List<Ingredient>? get() = lines.rescaled(scale, 1.0).toIngredients()
        val rawTotal: Nutrients get() = lines.filledIn().map { it.nutrients }.sum()
        val emptyPotGrams: Double get() = if (potOnScale) parseGrams(emptyPotText) ?: 0.0 else 0.0
        /** The cooked food: the reading minus the picked pan, or minus the empty pot when it stayed on the scale. */
        val cookedGrams: Double?
            get() = if (pan == null && emptyPotGrams > 0.0) {
                cookedFromReading(parseGrams(cookedText)?.minus(emptyPotGrams), null)
            } else {
                cookedFromReading(parseGrams(cookedText), pan)
            }
        val pieces: Int? get() = parsePieces(piecesText)
        val canSave: Boolean
            get() = name.isNotBlank() && lines.isNotEmpty() && lines.toIngredients() != null && cookedGrams != null &&
                (piecesText.isBlank() || pieces != null)
        val canDelete: Boolean get() = editing != null && !hasPortions
        /** What went in is complete enough to dish up: a name and every ingredient weighed. */
        val canDishUp: Boolean
            get() = editing == null && name.isNotBlank() && lines.isNotEmpty() && lines.toIngredients() != null &&
                (piecesText.isBlank() || pieces != null)
        val dishTotal: Double get() = dishedTotal(dishRows, restText)
        val canFinishDishUp: Boolean get() = canDishUp && dishTotal > 0.0
        fun nameOf(userId: Id?): String? = members.firstOrNull { it.userId == userId }?.displayName?.takeIf { it.isNotBlank() }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** What the screen started with (a recipe's lines, or the kept batch): no need to keep that again. */
    private var loadedDraft: BatchDraft? = null
    /** Set when Save is tapped, so a change still waiting to be kept can't bring the batch back as a draft. */
    @Volatile private var finished = false

    init {
        viewModelScope.launch {
            val user = session.currentUser.value
            _state.update { it.copy(myUserId = user.userId) }
            recipes.householdMembers(user.householdId).collect { list -> _state.update { it.copy(members = list) } }
        }
        val batchId = savedState.get<String>(RecipesRoutes.ARG_BATCH_ID)?.takeIf { it.isNotEmpty() }
        if (batchId != null) {
            loadForEdit(batchId)
        } else {
            viewModelScope.launch {
                val draft = drafts.load()
                if (draft != null && draft.hasContent) resume(draft) else loadNew()
                keepDraft()
            }
        }
    }

    /** Saves a new batch to the phone as it is typed, a moment after each change. */
    @OptIn(FlowPreview::class)
    private fun keepDraft() {
        viewModelScope.launch {
            _state.filter { !it.loading }
                .map { it.toDraft() }
                .distinctUntilChanged()
                .filter { it != loadedDraft }
                .debounce(DRAFT_DELAY_MS)
                .collect { draft ->
                    if (finished) return@collect
                    if (draft.hasContent) drafts.save(draft) else drafts.clear()
                }
        }
    }

    private fun State.toDraft() = BatchDraft(
        recipeId = recipeId,
        name = name,
        lines = lines.map { DraftLine.of(it) },
        cookedText = cookedText,
        panId = pan?.id,
        piecesText = piecesText,
        cookedOn = cookedOn.toString(),
        scale = scale,
        saveToRecipe = saveToRecipe,
        potOnScale = potOnScale,
        emptyPotText = emptyPotText,
        dishingUp = dishingUp,
        dishRows = dishRows,
        restText = restText,
    )

    private suspend fun resume(draft: BatchDraft) {
        val recipe = draft.recipeId?.let { recipes.get(it) }
        val pan = draft.panId?.let { id ->
            runCatching { recipes.pans(session.currentUser.value.householdId).first() }.getOrDefault(emptyList()).firstOrNull { it.id == id }
        }
        val today = LocalDate.now()
        val old = _state.value
        _state.value = State(
            loading = false,
            members = old.members,
            myUserId = old.myUserId,
            dishingUp = draft.dishingUp,
            dishRows = draft.dishRows,
            restText = draft.restText,
            recipe = recipe,
            name = draft.name,
            lines = ingredientFoods.enrich(draft.lines.map { it.toEditable() }),
            cookedText = draft.cookedText,
            pan = pan,
            piecesText = draft.piecesText,
            cookedOn = RecipesRoutes.dateArg(draft.cookedOn, today).takeIf { cookDateAllowed(it, today) } ?: today,
            scale = draft.scale,
            saveToRecipe = draft.saveToRecipe,
            potOnScale = draft.potOnScale,
            emptyPotText = draft.emptyPotText,
            resumed = true,
        )
        loadedDraft = _state.value.toDraft()
    }

    /** "Start over": the kept batch is thrown away and the screen starts as it was opened. */
    fun startOver() {
        viewModelScope.launch {
            drafts.clear()
            loadNew()
        }
    }

    private suspend fun loadNew() {
        val recipeId = savedState.get<String>(RecipesRoutes.ARG_RECIPE_ID)?.takeIf { it.isNotEmpty() }
        val viewedDay = RecipesRoutes.dateArg(savedState[RecipesRoutes.ARG_DATE], LocalDate.now())
        val recipe = recipeId?.let { recipes.get(it) }
        val old = _state.value
        _state.value = State(
            loading = false,
            members = old.members,
            myUserId = old.myUserId,
            recipe = recipe,
            cookedOn = viewedDay,
            name = recipe?.name.orEmpty(),
            lines = ingredientFoods.enrich(recipe?.ingredients.orEmpty().map { EditableIngredient.of(it) }),
            piecesText = recipe?.pieces?.toString().orEmpty(),
        )
        loadedDraft = _state.value.toDraft()
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
    fun setPotOnScale(on: Boolean) = _state.update { it.copy(potOnScale = on, potReadingTooLow = false) }
    fun setEmptyPot(text: String) = _state.update { it.copy(emptyPotText = text) }

    fun startWeighing(target: Weighing) = _state.update { it.copy(weighing = target) }
    fun onWeighed(grams: Double) {
        val s = _state.value
        when (val target = s.weighing) {
            is Weighing.Line -> if (s.potOnScale) {
                val alreadyIn = s.lines.filter { it.key != target.key }.mapNotNull { it.grams }.sum()
                val added = ingredientFromPot(grams, s.emptyPotGrams, alreadyIn)
                if (added != null) setGrams(target.key, formatGrams(added))
                _state.update { it.copy(potReadingTooLow = added == null) }
            } else {
                setGrams(target.key, formatGrams(grams))
            }
            Weighing.EmptyPot -> setEmptyPot(formatGrams(grams))
            Weighing.Cooked -> setCooked(formatGrams(grams))
            is Weighing.Dish -> setDishGrams(target.key, formatGrams(grams))
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
        finished = true
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
            drafts.clear()
            val amounts = s.recipeAmounts
            if (s.saveToRecipe && s.canSaveToRecipe && amounts != null) s.recipe?.let { recipes.save(it.copy(ingredients = amounts)) }
            _state.update { it.copy(savedBatchId = batch.id) }
        }
    }

    /** "Dish up" (D1): starts with my plate and a plate for everyone else in the household. */
    fun startDishUp() = _state.update { s ->
        if (!s.canDishUp) return@update s
        val rows = s.dishRows.ifEmpty { startingRows(session.currentUser.value.userId, s.members) }
        s.copy(dishingUp = true, dishRows = rows)
    }

    /** Back to the ingredients; the plates weighed so far stay. */
    fun backToCooking() = _state.update { it.copy(dishingUp = false) }

    fun addDishRow(kind: DishKind) = _state.update { it.copy(dishRows = it.dishRows + DishRow(kind = kind)) }
    fun removeDishRow(key: String) = _state.update { s -> s.copy(dishRows = s.dishRows.filterNot { it.key == key }) }
    fun setDishGrams(key: String, text: String) = _state.update { s ->
        s.copy(dishRows = s.dishRows.map { if (it.key == key) it.copy(gramsText = text) else it })
    }
    /** A plate for someone else, or a guest; a box to the fridge or the freezer. */
    fun setDishKind(key: String, kind: DishKind, forUserId: Id? = null) = _state.update { s ->
        s.copy(dishRows = s.dishRows.map { if (it.key == key) it.copy(kind = kind, forUserId = forUserId) else it })
    }
    fun setRest(text: String) = _state.update { it.copy(restText = text) }
    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }

    /**
     * "That's everything" (D1): the batch is the sum of all plates and boxes. My plate is logged
     * on the cook day; other people's plates wait for them; boxes go on the fridge or freezer list.
     * The batch itself leaves the pot list, since nothing is left in the pot.
     */
    fun finishDishUp() {
        val s = _state.value
        if (!s.canFinishDishUp) return
        val ingredients = s.lines.toIngredients() ?: return
        val user = session.currentUser.value
        finished = true
        val batch = Batch(
            id = newId(),
            householdId = user.householdId,
            recipeId = s.recipeId,
            name = s.name.trim(),
            ingredients = ingredients,
            cookedWeightG = s.dishTotal,
            cookedOn = s.cookedOn,
            finishedOn = LocalDate.now(),
            pieces = s.pieces,
        )
        val outcome = dishOutcome(batch, user.userId, s.dishRows, s.restText)
        viewModelScope.launch {
            recipes.saveBatch(batch)
            outcome.myGrams?.let { grams ->
                val entry = batch.logEntry(user.userId, s.cookedOn, s.meal, grams, Instant.now())
                diary.save(entry)
                recipes.savePortion(batch.portionFor(entry, user.householdId))
            }
            outcome.portions.forEach { recipes.savePortion(it) }
            outcome.containers.forEach { recipes.saveContainer(it) }
            drafts.clear()
            val amounts = s.recipeAmounts
            if (s.saveToRecipe && s.canSaveToRecipe && amounts != null) s.recipe?.let { recipes.save(it.copy(ingredients = amounts)) }
            _state.update { it.copy(dishedUp = true) }
        }
    }

    private companion object {
        const val DRAFT_DELAY_MS = 500L
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

/**
 * Cooking a batch, step 3 (and for the other person, the only step): weigh my portion and log my share.
 * With a box (round 6, D3) the portion is taken from that fridge or freezer box, and the box
 * shows what is left in it afterwards.
 */
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
        /** The box this portion comes from (D3); null = from the pot. */
        val box: BatchContainer? = null,
    ) {
        val boxLeft: Double? get() = box?.gramsLeft(portions)
        /** What stays in the box after this portion. */
        val boxLeftAfter: Double? get() = boxLeft?.let { left -> (left - (grams ?: 0.0)).coerceAtLeast(0.0) }
        val grams: Double? get() = parseGrams(gramsText)
        val nutrients: Nutrients? get() = grams?.let { g -> batch?.shareFor(g) }
        val gramsLeft: Double? get() = batch?.gramsLeft(portions)
        val piecesLeft: Double? get() = gramsLeft?.let { left -> batch?.piecesLeft(left) }
        val canLog: Boolean get() = batch != null && grams != null
    }

    private val batchId: String = checkNotNull(savedState[RecipesRoutes.ARG_BATCH_ID])
    private val boxId: String? = savedState.get<String>(RecipesRoutes.ARG_BOX)?.takeIf { it.isNotEmpty() }
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
            val box = boxId?.let { id ->
                runCatching { recipes.containers(session.currentUser.value.householdId).first() }.getOrDefault(emptyList())
                    .firstOrNull { it.id == id }
            }
            _state.update { it.copy(loading = false, batch = batch, box = box) }
        }
    }

    fun setGrams(text: String) = _state.update { if (it.gramsText == text) it else it.copy(gramsText = text, portionLabel = null) }

    /** A pieces chip (S11): [count] pieces of the batch, remembered on the diary line as [label]. */
    fun setPieces(count: Double, label: String) = _state.update { s ->
        val grams = s.batch?.piecesGrams(count) ?: return@update s
        s.copy(gramsText = formatGrams(grams), portionLabel = label)
    }
    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }

    /** "Whole box" (D3): everything still in the box. */
    fun takeWholeBox() = _state.update { s ->
        val left = s.boxLeft?.takeIf { it > 0.0 } ?: return@update s
        s.copy(gramsText = formatGrams(left), portionLabel = null)
    }
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
            recipes.savePortion(batch.portionFor(entry, user.householdId).copy(containerId = s.box?.id))
            if (s.lastPortion) recipes.finishBatch(batch.id, LocalDate.now())
            _state.update { it.copy(done = true) }
        }
    }
}
