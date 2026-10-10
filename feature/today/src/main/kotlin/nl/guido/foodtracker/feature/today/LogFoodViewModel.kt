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
import nl.guido.foodtracker.core.model.CommonPortions
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.ui.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
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
    /** Set when logging for an earlier day (opened from that day on Today); null = today. */
    val date: LocalDate?,
    val meal: Meal,
    val query: String,
    val recent: List<LogEntry>,
    val pinned: List<Recipe>,
    val pinnedFoods: List<PinnedFood>,
    val batches: List<PotItem>,
    val found: List<Food>,
    val online: OnlineSearch,
    val amount: AmountStep?,
) {
    /** My pin for the food in the amount sheet, if I pinned it. */
    val amountPin: PinnedFood? get() = amount?.pick?.foodId?.let { id -> pinnedFoods.firstOrNull { it.food.id == id } }
}

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
    private val recipes: RecipeRepository,
    /** "1 apple ≈ 150 g" pieces, bound by feature/food; empty until it is. */
    private val commonPortions: Optional<CommonPortions>,
) : ViewModel() {

    private val meal = MutableStateFlow(
        savedState.get<String>(TodayRoutes.ARG_MEAL)?.let { name -> Meal.entries.firstOrNull { it.name == name } }
            ?: mealForTime(LocalTime.now()),
    )
    /** The day lines go to: the day shown on Today when it opened this screen, else today. */
    private val date: LocalDate? = parseDate(savedState.get<String>(TodayRoutes.ARG_DATE))?.takeIf { it < LocalDate.now() }
    private fun day(): LocalDate = date ?: LocalDate.now()

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

    /** My favourites (per person): recipes with my usual portion, and pinned foods with my usual amount. */
    private val favourites = user.flatMapLatest { recipes.favourites(it.userId) }
    private val pinned = favourites.combine(household) { favourites, list -> pinnedRecipes(favourites, list) }
    private val myPinnedFoods = favourites.mapLatest { list -> pinnedFoods(list) { foods.get(it) } }

    /** Batches stay until someone taps "Finished", with what is left in the pot across the household. */
    private val batches = user.flatMapLatest { u ->
        combine(recipes.batches(u.householdId), recipes.householdPortions(u.householdId)) { list, portions ->
            potItems(list, portions)
        }
    }

    private val lists = combine(recentAll, pinned, myPinnedFoods, batches, found) { r, p, pf, b, f -> Lists(r, p, pf, b, f) }

    val state: StateFlow<LogFoodUiState?> = combine(meal, query, lists, online, amount) { meal, query, lists, online, amount ->
        val recent = recentItems(lists.recent, meal).filter { query.isBlank() || it.displayName.contains(query.trim(), true) }
        val recentFoodIds = recent.mapNotNull { (it.what as? Logged.FoodRef)?.foodId }.toSet()
        LogFoodUiState(
            date = date,
            meal = meal,
            query = query,
            recent = recent,
            pinned = if (query.isBlank()) lists.pinned else lists.pinned.filter { it.name.contains(query.trim(), true) },
            pinnedFoods = if (query.isBlank()) lists.pinnedFoods
            else lists.pinnedFoods.filter { it.food.name.contains(query.trim(), true) },
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
                val local = state.value?.found.orEmpty().map { it.id }.toSet()
                val results = foodSource.searchOnline(text).filterNot { it.id in local }
                if (results.isEmpty()) OnlineSearch.NothingFound else OnlineSearch.Found(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OnlineSearch.NotReachable
            }
        }
    }

    fun choose(food: Food) {
        val pick = pickFromFood(food, recentAll.value)
        amount.value = AmountStep(pick, weighedGrams ?: 0.0).withDefault()
        addChips(pick, food)
    }

    fun choose(entry: LogEntry) {
        val pick = pickFromEntry(entry)
        if (pick == null) return quickAdd(entry)
        amount.value = AmountStep(pick, weighedGrams ?: pick.defaultGrams)
        // Recent lines don't carry the food's serving and pack sizes: look them up for the chips.
        val foodId = pick.foodId ?: return
        viewModelScope.launch { foods.get(foodId)?.let { addChips(pick, it) } }
    }

    /** Fills in the amount chips for [food] (its sizes and common pieces) while [pick] is still open. */
    private fun addChips(pick: Pick, food: Food) {
        viewModelScope.launch {
            val pieces = try {
                commonPortions.getOrNull()?.forFood(food).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val chips = amountChips(food, pieces)
            amount.update { step ->
                if (step != null && step.pick == pick) step.copy(pick = pick.copy(food = food, chips = chips)) else step
            }
        }
    }

    /** A pinned food: its amount sheet starts at my usual amount. */
    fun choose(pin: PinnedFood) {
        val pick = pickFromFood(pin.food, recentAll.value)
        amount.value = AmountStep(pick, weighedGrams ?: pin.usualGrams ?: pick.defaultGrams)
        addChips(pick, pin.food)
    }

    /** Pins the food in the amount sheet with [grams] as my usual amount, or updates that amount. */
    fun pin(grams: Double) {
        val step = amount.value ?: return
        val foodId = step.pick.foodId ?: return
        val existing = state.value?.amountPin
        viewModelScope.launch {
            step.pick.food?.let { food -> if (foods.get(food.id) == null) foods.save(food) }
            val favourite = existing?.favourite?.copy(usualPortion = Portion(grams))
                ?: foodFavourite(user.value.userId, foodId, grams)
            recipes.saveFavourite(favourite)
        }
    }

    fun unpin() {
        val pin = state.value?.amountPin ?: return
        viewModelScope.launch { recipes.deleteFavourite(pin.favourite.id) }
    }

    fun setGrams(grams: Double) = amount.update { it?.copy(grams = grams) }

    fun closeAmount() {
        amount.value = null
    }

    /** One tap: log a recent item again with the same amount. */
    fun quickAdd(entry: LogEntry) {
        viewModelScope.launch {
            val line = repeatEntry(
                entry, user.value.userId, day(),
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
                step.pick, grams, user.value.userId, day(),
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
        viewModelScope.launch { events.send(LogFoodEvent.Open(Routes.recipeLog(recipe.id, meal.value, date))) }
    }

    fun openBatch(batch: Batch) {
        viewModelScope.launch { events.send(LogFoodEvent.Open(Routes.batchPortion(batch.id, meal.value, date))) }
    }

    /**
     * The camera came back: a scanned food already has its amount (the camera asked "How much?"),
     * so it is added at once with Undo; a weight on its own fills in the amount.
     */
    fun onCameraResult(foodId: Id?, grams: Double?) {
        when {
            foodId != null && grams != null && grams > 0 -> addScanned(foodId, grams)
            foodId != null -> openFood(foodId, grams)
            grams != null && amount.value != null -> setGrams(grams)
            grams != null -> weighedGrams = grams
        }
    }

    private fun addScanned(foodId: Id, grams: Double) {
        viewModelScope.launch {
            val food = foods.get(foodId) ?: return@launch
            val line = cameraEntry(
                food, grams, user.value.userId, day(), mealFor(food.isDrink, meal.value), Instant.now(),
            )
            diary.save(line)
            weighedGrams = null
            events.send(LogFoodEvent.Added(line.displayName, line.meal, line.id))
        }
    }

    private fun openFood(foodId: Id, grams: Double?) {
        viewModelScope.launch {
            val food = foods.get(foodId) ?: return@launch
            if (food.isDrink) meal.value = Meal.DRINKS
            val recent = recentAll.value.ifEmpty { diary.recent(user.value.userId, RECENT_LINES).first() }
            val pick = pickFromFood(food, recent)
            amount.value = AmountStep(pick, grams ?: pick.defaultGrams)
            addChips(pick, food)
        }
    }

    private fun AmountStep.withDefault() = if (grams > 0) this else copy(grams = pick.defaultGrams)

    private data class Lists(
        val recent: List<LogEntry>,
        val pinned: List<Recipe>,
        val pinnedFoods: List<PinnedFood>,
        val batches: List<PotItem>,
        val found: List<Food>,
    )

    private companion object {
        const val RECENT_LINES = 200
        const val SEARCH_DELAY_MS = 200L
    }
}
