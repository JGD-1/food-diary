package nl.guido.foodtracker.feature.food.eatout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.estimate.EstimateFailure
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class EatOutState(
    val dish: String = "",
    val meal: Meal = mealAt(LocalTime.now()),
    val loading: Boolean = false,
    /** The estimate for [estimatedDish]; cleared when the dish text changes. */
    val estimate: Estimate? = null,
    val estimatedDish: String = "",
    val failure: EstimateFailure.Reason? = null,
    val typedKcal: String = "",
    val logged: Boolean = false,
)

@HiltViewModel
class EatOutViewModel @Inject constructor(
    private val estimator: RestaurantEstimator,
    private val diary: DiaryRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EatOutState())
    val state: StateFlow<EatOutState> = _state.asStateFlow()

    fun setDish(text: String) = _state.update {
        if (text.trim() == it.estimatedDish) it.copy(dish = text)
        else it.copy(dish = text, estimate = null, failure = null)
    }

    fun setMeal(meal: Meal) = _state.update { it.copy(meal = meal) }

    fun setTypedKcal(text: String) = _state.update { it.copy(typedKcal = text.filter(Char::isDigit).take(4)) }

    fun estimate() {
        val dish = _state.value.dish.trim()
        if (dish.isEmpty() || _state.value.loading) return
        _state.update { it.copy(loading = true, failure = null, estimate = null) }
        viewModelScope.launch {
            val result = try {
                Result.success(estimator.estimate(dish))
            } catch (e: EstimateFailure) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(EstimateFailure(EstimateFailure.Reason.FAILED))
            }
            _state.update { s ->
                s.copy(
                    loading = false,
                    estimate = result.getOrNull(),
                    estimatedDish = dish,
                    failure = (result.exceptionOrNull() as? EstimateFailure)?.reason,
                )
            }
        }
    }

    /** Logs the estimate, or the typed kcal when there is no estimate. */
    fun log() {
        val s = _state.value
        val dish = s.dish.trim()
        val estimate = s.estimate ?: typedKcal(s.typedKcal)?.let(::typedEstimate) ?: return
        if (dish.isEmpty() || s.logged) return
        _state.update { it.copy(logged = true) }
        viewModelScope.launch {
            val user = session.currentUser.value
            diary.save(restaurantEntry(user.userId, LocalDate.now(), s.meal, dish, estimate, Instant.now()))
        }
    }
}
