package nl.guido.foodtracker.feature.food.eatout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.ui.Routes
import nl.guido.foodtracker.feature.food.dishes.Dish
import nl.guido.foodtracker.feature.food.dishes.DishRepository
import nl.guido.foodtracker.feature.food.dishes.dishKey
import nl.guido.foodtracker.feature.food.dishes.matchDishes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class EatOutState(
    val dish: String = "",
    val meal: Meal = mealAt(LocalTime.now()),
    /** Dishes from the list that match what was typed. */
    val suggestions: List<Dish> = emptyList(),
    /** The dish picked from the list; cleared when the text changes. */
    val picked: Dish? = null,
    val typedKcal: String = "",
    /** How much of the picked dish's usual portion was eaten. */
    val size: PortionSize = PortionSize.WHOLE,
    val logged: Boolean = false,
)

@HiltViewModel
class EatOutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dishes: DishRepository,
    private val diary: DiaryRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EatOutState())
    val state: StateFlow<EatOutState> = _state.asStateFlow()
    private var matching: Job? = null

    /** The day being logged to: the day Today was showing (Routes.eatOut(date)), else today. */
    private val day: LocalDate = dayFrom(savedStateHandle.get<String>(Routes.ARG_DATE), LocalDate.now())

    init {
        // "Type kcal yourself" in Log food opens this screen with the words already typed there.
        savedStateHandle.get<String>(Routes.ARG_DISH)?.takeIf { it.isNotBlank() }?.let { setDish(it.trim()) }
    }

    fun setDish(text: String) {
        _state.update { if (it.picked != null && dishKey(text) == dishKey(it.picked.name)) it.copy(dish = text) else it.copy(dish = text, picked = null) }
        matching?.cancel()
        matching = viewModelScope.launch {
            val found = matchDishes(text, dishes.all())
            _state.update { it.copy(suggestions = found) }
        }
    }

    fun pick(dish: Dish) =
        _state.update { it.copy(dish = dish.name, picked = dish, suggestions = emptyList(), size = PortionSize.WHOLE) }

    fun setSize(size: PortionSize) = _state.update { it.copy(size = size) }

    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }

    fun setTypedKcal(text: String) = _state.update { it.copy(typedKcal = text.filter(Char::isDigit).take(4)) }

    /** Logs the picked dish's range, or the typed kcal (and remembers that dish for next time). */
    fun log() {
        val s = _state.value
        val name = s.dish.trim()
        if (name.isEmpty() || s.logged) return
        val typed = if (s.picked == null) typedKcal(s.typedKcal) ?: return else null
        // The portion chips scale a dish from the list; a typed kcal is already the person's own number.
        val estimate = s.picked?.estimate?.scaled(s.size) ?: typedEstimate(typed!!)
        val logName = if (s.picked != null) dishWithSize(name, s.size) else name
        _state.update { it.copy(logged = true) }
        viewModelScope.launch {
            if (typed != null) dishes.saveOwn(name, typed)
            val user = session.currentUser.value
            diary.save(restaurantEntry(user.userId, day, s.meal, logName, estimate, Instant.now()))
        }
    }
}
