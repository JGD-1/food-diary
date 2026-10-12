package nl.guido.foodtracker.feature.recipes.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodNames
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.PickedFood
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.recipes.IngredientFoods
import nl.guido.foodtracker.feature.recipes.logic.NoteIngredient
import nl.guido.foodtracker.feature.recipes.logic.NoteLine
import nl.guido.foodtracker.feature.recipes.logic.NoteReading
import nl.guido.foodtracker.feature.recipes.logic.ReadLine
import nl.guido.foodtracker.feature.recipes.logic.SeenLine
import nl.guido.foodtracker.feature.recipes.logic.closestName
import nl.guido.foodtracker.feature.recipes.logic.encodeNoteIngredients
import nl.guido.foodtracker.feature.recipes.logic.parseNoteLine
import nl.guido.foodtracker.feature.recipes.logic.pickFood
import nl.guido.foodtracker.feature.recipes.logic.searchTries
import java.util.Optional
import javax.inject.Inject

/**
 * "Scan a note" (round 4, H): hold the phone over a handwritten list and the lines come in as
 * they are read, like the barcode scanner. Then each line gets its food and amount, worked out as
 * well as possible, and is checked before anything is added.
 */
@HiltViewModel
internal class NoteScanViewModel @Inject constructor(
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val foodSource: FoodSource,
    private val foods: FoodRepository,
    private val ingredientFoods: IngredientFoods,
    private val names: Optional<FoodNames>,
) : ViewModel() {
    enum class Phase { SCANNING, MATCHING, CHECKING }

    /** One line on the check list. */
    data class Item(
        val key: String,
        /** The line as read from the note. */
        val read: String,
        val food: Food? = null,
        /** The food's readable name, else its own name. */
        val title: String? = null,
        val grams: Double? = null,
        /** "2 eggs", "1 tin"; null when the note said grams. */
        val label: String? = null,
        val selected: Boolean = false,
    )

    data class State(
        val phase: Phase = Phase.SCANNING,
        /** Lines read so far while scanning, in note order. */
        val lines: List<ReadLine> = emptyList(),
        val items: List<Item> = emptyList(),
        /** The line whose food is being changed in the shared picker. */
        val changing: String? = null,
    ) {
        val chosen: List<Item> get() = items.filter { it.selected && it.food != null }
    }

    private val reading = NoteReading()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** One camera frame's lines (called on the main thread). */
    fun onFrame(lines: List<SeenLine>) {
        if (_state.value.phase != Phase.SCANNING) return
        reading.offer(lines)
        _state.update { it.copy(lines = reading.lines) }
    }

    fun startAgain() {
        reading.clear()
        _state.update { State() }
    }

    /** Back to the camera to read more; what was read stays. */
    fun scanMore() = _state.update { it.copy(phase = Phase.SCANNING) }

    /** "Check these lines": find a food and amount for each line. */
    fun check() {
        val lines = _state.value.lines
        if (lines.isEmpty()) return
        _state.update { it.copy(phase = Phase.MATCHING) }
        viewModelScope.launch {
            val known = knownIngredients()
            val items = lines.mapNotNull { match(it, known) }
            _state.update { it.copy(phase = Phase.CHECKING, items = items) }
        }
    }

    fun toggle(key: String) = _state.update { s ->
        s.copy(items = s.items.map { if (it.key == key && it.food != null) it.copy(selected = !it.selected) else it })
    }

    fun startChange(key: String) = _state.update { it.copy(changing = key) }

    /** The food chosen in the shared picker replaces the line's food, with the amount chosen there. */
    fun onPicked(picked: PickedFood) {
        val key = _state.value.changing ?: return
        viewModelScope.launch {
            val food = runCatching { foods.get(picked.foodId) }.getOrNull() ?: return@launch
            _state.update { s ->
                s.copy(
                    changing = null,
                    items = s.items.map {
                        if (it.key == key) it.copy(food = food, title = titleOf(food), grams = picked.grams, label = picked.label, selected = true) else it
                    },
                )
            }
        }
    }

    /** The ticked lines, for the screen that opened the scanner. */
    fun result(): String = encodeNoteIngredients(
        _state.value.chosen.map { NoteIngredient(it.food!!.id, it.grams, it.label) },
    )

    /** Ingredients used before in this household's recipes and batches: by id, and by name for misread words. */
    private data class Known(val ids: Set<Id>, val byName: Map<String, Id>)

    private suspend fun knownIngredients(): Known {
        val household = session.currentUser.value.householdId
        val used = runCatching {
            recipes.recipes(household).first().flatMap { it.ingredients } + recipes.batches(household).first().flatMap { it.ingredients }
        }.getOrDefault(emptyList())
        return Known(used.mapTo(HashSet()) { it.foodId }, used.associate { it.name to it.foodId })
    }

    /**
     * The best food and amount for one note line. The most-read version of the line is tried
     * first, then the next ones, so a line misread most of the time can still be found.
     */
    private suspend fun match(line: ReadLine, known: Known): Item? {
        var firstParsed: NoteLine? = null
        for (version in line.versions.take(TRIED_VERSIONS)) {
            val parsed = parseNoteLine(version) ?: continue
            if (firstParsed == null) firstParsed = parsed
            val food = findFood(parsed.food, known) ?: continue
            return itemFor(parsed, food)
        }
        val parsed = firstParsed ?: return null
        return Item(key = newId(), read = parsed.read)
    }

    private suspend fun findFood(words: String, known: Known): Food? {
        for (text in searchTries(words)) {
            val found = runCatching { foodSource.search(text) }.getOrDefault(emptyList())
            pickFood(found, known.ids)?.let { return it }
        }
        // A word read badly: the closest ingredient used before ("gehakl" → "gehakt").
        val name = closestName(words, known.byName.keys) ?: return null
        return runCatching { foods.get(known.byName.getValue(name)) }.getOrNull()
    }

    private suspend fun itemFor(parsed: NoteLine, food: Food): Item {
        val amount = parsed.amount(
            pieceGrams = if (parsed.count != null) ingredientFoods.pieceGrams(food) else null,
            servingG = food.servingG,
            packageG = food.packageG,
        )
        return Item(
            key = newId(),
            read = parsed.read,
            food = food,
            title = titleOf(food),
            grams = amount?.grams,
            label = amount?.label,
            selected = true,
        )
    }

    private fun titleOf(food: Food): String =
        (if (names.isPresent) runCatching { names.get().plain(food) }.getOrNull() else null) ?: food.name

    private companion object {
        const val TRIED_VERSIONS = 3
    }
}
