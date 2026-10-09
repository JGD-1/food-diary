package nl.guido.foodtracker.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Recipe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** The amount step for one chosen item. */
internal data class AmountStep(val pick: Pick, val grams: Double)

internal sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data object Searching : OnlineSearch
    data class Found(val foods: List<Food>) : OnlineSearch
    data object NothingFound : OnlineSearch
    data object NotReachable : OnlineSearch
}

internal data class LogFoodUiState(
    val meal: Meal,
    val query: String,
    val recent: List<LogEntry>,
    val pinned: List<Recipe>,
    val batches: List<Batch>,
    val found: List<Food>,
    val online: OnlineSearch,
    val amount: AmountStep?,
)

internal sealed interface LogFoodEvent {
    data class Added(val name: String, val meal: Meal, val id: Id) : LogFoodEvent
    data class Open(val route: String) : LogFoodEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class LogFoodViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val diary: DiaryRepository,
    private val foods: FoodRepository,
    private val foodSource: FoodSource,
    recipes: RecipeRepository,
) : ViewModel() {

    private val meal = MutableStateFlow(
        savedState.get<String>(TodayRoutes.ARG_MEAL)?.let { name -> Meal.entries.firstOrNull { it.name == name } }
            ?: mealForTime(LocalTime.now()),
    )
    private val query = MutableStateFlow("")
    private val online = MutableStateFlow<OnlineSearch>(OnlineSearch.Idle)
    private val amount = MutableStateFlow<AmountStep?>(null)

    /** A weight that came from the scale before a food was chosen ("Weigh" first, then pick). */
    private var weighedGrams: Double? = savedState.get<String>(TodayRoutes.ARG_GRAMS)?.toDoubleOrNull()

    private val events = Channel<LogFoodEvent>(Channel.BUFFERED)
    val eventFlow: Flow<LogFoodEvent> = events.receiveAsFlow()

    private val user = session.currentUser

    private val recentAll: StateFlow<List<LogEntry>> = user
        .flatMapLatest { diary.recent(it.userId, RECENT_LINES) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val found: Flow<List<Food>> = query.debounce(SEARCH_DELAY_MS).mapLatest { text ->
        if (text.isBlank()) emptyList()
        else try {
            foodSource.search(text.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val household = user.flatMapLatest { recipes.recipes(it.householdId) }
    private val pinned = household.map { list -> list.filter { it.pinned } }
    private val batches = user.flatMapLatest { recipes.batches(it.householdId) }.map { list ->
        val since = LocalDate.now().minusDays(RECENT_BATCH_DAYS)
        list.filter { it.cookedOn >= since }
    }

    private val lists = combine(recentAll, pinned, batches, found) { r, p, b, f -> Lists(r, p, b, f) }

    val state: StateFlow<LogFoodUiState?> = combine(meal, query, lists, online, amount) { meal, query, lists, online, amount ->
        val recent = recentItems(lists.recent, meal).filter { query.isBlank() || it.displayName.contains(query.trim(), true) }
        val recentFoodIds = recent.mapNotNull { (it.what as? Logged.FoodRef)?.foodId }.toSet()
        LogFoodUiState(
            meal = meal,
            query = query,
            recent = recent,
            pinned = if (query.isBlank()) lists.pinned else lists.pinned.filter { it.name.contains(query.trim(), true) },
            batches = if (query.isBlank()) lists.batches else emptyList(),
            found = lists.found.filterNot { it.id in recentFoodIds },
            online = online,
            amount = amount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        savedState.get<String>(TodayRoutes.ARG_FOOD_ID)?.let { openFood(it, weighedGrams) }
    }

    fun setMeal(value: Meal) = meal.update { value }

    fun setQuery(text: String) {
        query.value = text
        online.value = OnlineSearch.Idle
    }

    /** Only on request: Open Food Facts allows few searches a minute, so no searching while typing. */
    fun searchOnline() {
        val text = query.value.trim()
        if (text.isEmpty()) return
        online.value = OnlineSearch.Searching
        viewModelScope.launch {
            online.value = try {
                // Switches to FoodSource.searchOnline once that lands in core/model (lead).
                val local = state.value?.found.orEmpty().map { it.id }.toSet()
                val results = foodSource.search(text).filterNot { it.id in local }
                if (results.isEmpty()) OnlineSearch.NothingFound else OnlineSearch.Found(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OnlineSearch.NotReachable
            }
        }
    }

    fun choose(food: Food) {
        amount.value = AmountStep(pickFromFood(food, recentAll.value), weighedGrams ?: 0.0).withDefault()
    }

    fun choose(entry: LogEntry) {
        val pick = pickFromEntry(entry)
        if (pick == null) quickAdd(entry) else amount.value = AmountStep(pick, weighedGrams ?: pick.defaultGrams)
    }

    fun setGrams(grams: Double) = amount.update { it?.copy(grams = grams) }

    fun closeAmount() {
        amount.value = null
    }

    /** One tap: log a recent item again with the same amount. */
    fun quickAdd(entry: LogEntry) {
        viewModelScope.launch {
            val line = repeatEntry(
                entry, user.value.userId, LocalDate.now(),
                mealFor(entry.meal == Meal.DRINKS, meal.value), Instant.now(),
            )
            diary.save(line)
            events.send(LogFoodEvent.Added(line.displayName, line.meal, line.id))
        }
    }

    fun add(grams: Double) {
        val step = amount.value ?: return
        viewModelScope.launch {
            step.pick.food?.let { food -> if (foods.get(food.id) == null) foods.save(food) }
            val line = entryFromPick(
                step.pick, grams, user.value.userId, LocalDate.now(),
                mealFor(step.pick.isDrink, meal.value), Instant.now(),
            )
            diary.save(line)
            amount.value = null
            weighedGrams = null
            events.send(LogFoodEvent.Added(line.displayName, line.meal, line.id))
        }
    }

    fun undo(id: Id) {
        viewModelScope.launch { diary.delete(id) }
    }

    fun openRecipe(recipe: Recipe) {
        viewModelScope.launch { events.send(LogFoodEvent.Open(TodayRoutes.recipeLog(recipe.id))) }
    }

    fun openBatch(batch: Batch) {
        viewModelScope.launch { events.send(LogFoodEvent.Open(TodayRoutes.batchPortion(batch.id))) }
    }

    /** The camera came back: a scanned food opens its amount step; a weight fills in the amount. */
    fun onCameraResult(foodId: Id?, grams: Double?) {
        when {
            foodId != null -> openFood(foodId, grams)
            grams != null && amount.value != null -> setGrams(grams)
            grams != null -> weighedGrams = grams
        }
    }

    private fun openFood(foodId: Id, grams: Double?) {
        viewModelScope.launch {
            val food = foods.get(foodId) ?: return@launch
            if (food.isDrink) meal.value = Meal.DRINKS
            val recent = recentAll.value.ifEmpty { diary.recent(user.value.userId, RECENT_LINES).first() }
            val pick = pickFromFood(food, recent)
            amount.value = AmountStep(pick, grams ?: pick.defaultGrams)
        }
    }

    private fun AmountStep.withDefault() = if (grams > 0) this else copy(grams = pick.defaultGrams)

    private data class Lists(
        val recent: List<LogEntry>,
        val pinned: List<Recipe>,
        val batches: List<Batch>,
        val found: List<Food>,
    )

    private companion object {
        const val RECENT_LINES = 200
        const val SEARCH_DELAY_MS = 200L
        const val RECENT_BATCH_DAYS = 5L
    }
}
