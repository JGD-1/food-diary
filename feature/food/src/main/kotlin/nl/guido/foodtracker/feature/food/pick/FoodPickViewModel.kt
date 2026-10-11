package nl.guido.foodtracker.feature.food.pick

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.ui.FoodPickPurpose
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.food.names.BundledFoodNames
import nl.guido.foodtracker.feature.food.pieces.BundledCommonPortions
import javax.inject.Inject

/** Online search (Open Food Facts) only on request: it allows few searches a minute, so never while typing. */
internal sealed interface OnlinePick {
    data object Idle : OnlinePick
    data object Searching : OnlinePick
    data class Found(val foods: List<Food>) : OnlinePick
    data object NothingFound : OnlinePick
    data object NotReachable : OnlinePick
}

/** The food waiting for its amount, with its chips. */
internal data class PickAmount(val food: Food, val readableName: String?, val chips: List<PickChip>, val pieceWords: PieceWords?)

/** How the picker names pieces of the chosen food: "1 egg", "2 eggs". */
internal fun interface PieceWords {
    fun label(count: Int): String
}

internal data class FoodPickState(
    val purpose: FoodPickPurpose,
    val query: String = "",
    /** Recently used ingredients, shown before anything is typed. */
    val recent: List<Food> = emptyList(),
    /** Phone results for what is typed, recently used ones first. */
    val found: List<Food> = emptyList(),
    val online: OnlinePick = OnlinePick.Idle,
    val amount: PickAmount? = null,
)

/**
 * The shared food picker (S21) for recipe and batch ingredients and extras: search on the phone as you
 * type (S14), recently used ingredients first, then an amount with chips (S13).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class FoodPickViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val foodSource: FoodSource,
    private val foods: FoodRepository,
    recipes: RecipeRepository,
    session: SessionRepository,
    private val names: BundledFoodNames,
    private val pieces: BundledCommonPortions,
) : ViewModel() {
    private val purpose = savedState.get<String>(Routes.ARG_PURPOSE)
        ?.let { name -> FoodPickPurpose.entries.firstOrNull { it.name == name } } ?: FoodPickPurpose.RECIPE_INGREDIENT

    private val query = MutableStateFlow("")
    private val online = MutableStateFlow<OnlinePick>(OnlinePick.Idle)
    private val amount = MutableStateFlow<PickAmount?>(null)

    private val recentIds = session.currentUser.flatMapLatest { user ->
        combine(recipes.batches(user.householdId), recipes.recipes(user.householdId)) { b, r -> recentIngredientIds(b, r) }
    }
    private val recent = recentIds.mapLatest { ids -> ids.mapNotNull { orNull { foods.get(it) } } }

    private val found = query.debounce(SEARCH_DELAY_MS).mapLatest { text -> if (text.trim().length < MIN_LETTERS) emptyList() else search(text.trim()) }

    val state: StateFlow<FoodPickState> = combine(query, recent, found, online, amount) { query, recent, found, online, amount ->
        val ids = recent.map { it.id }
        val typed = query.trim().length >= MIN_LETTERS
        FoodPickState(
            purpose = purpose,
            query = query,
            recent = recent,
            found = if (typed) recentFirst(found, ids) else emptyList(),
            online = online,
            amount = amount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodPickState(purpose))

    fun setQuery(text: String) {
        query.value = text
        online.value = OnlinePick.Idle
    }

    fun searchOnline() {
        val text = query.value.trim()
        if (text.length < MIN_LETTERS || online.value == OnlinePick.Searching) return
        online.value = OnlinePick.Searching
        viewModelScope.launch {
            online.value = try {
                val local = state.value.found.map { it.id }.toSet()
                val results = foodSource.searchOnline(text).filterNot { it.id in local }
                if (results.isEmpty()) OnlinePick.NothingFound else OnlinePick.Found(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OnlinePick.NotReachable
            }
        }
    }

    fun choose(food: Food) {
        viewModelScope.launch {
            val piece = orNull { pieces.piece(food) }
            val language = pieces.language()
            amount.value = PickAmount(
                food = food,
                readableName = names.plain(food),
                chips = pickChips(food, piece, language),
                pieceWords = piece?.let { p -> PieceWords { count -> p.label(count, language) } },
            )
        }
    }

    fun closeAmount() {
        amount.value = null
    }

    /** The app's readable name for a NEVO food (S1); null when the NEVO name has no short forms. */
    fun readableName(food: Food): String? = names.plain(food)

    private suspend fun search(text: String): List<Food> {
        // The food source puts the plain, everyday food first; the plain text match only adds what it missed.
        val ranked = orNull { foodSource.search(text) }.orEmpty()
        val local = orNull { foods.search(text, 30).first() }.orEmpty()
        return (ranked + local).distinctBy { it.id }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 250L
        const val MIN_LETTERS = 2
    }
}

/** A failed lookup shows nothing instead of crashing; a cancelled one stays cancelled. */
private suspend fun <T> orNull(lookup: suspend () -> T): T? =
    try {
        lookup()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
