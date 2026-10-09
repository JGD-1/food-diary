package nl.guido.foodtracker.feature.food.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.food.estimate.EstimateRules
import nl.guido.foodtracker.feature.food.estimate.EstimateUnavailable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

internal data class EatOutState(
    val dish: String = "",
    val meal: Meal = Meal.DINNER,
    val step: Step = Step.Typing,
    val typedKcal: String = "",
) {
    sealed interface Step {
        data object Typing : Step
        data object Estimating : Step
        data class Result(val estimate: Estimate) : Step
        /** No estimate possible right now; the user can type a number or try again. */
        data class Unavailable(val reason: Reason) : Step
        data object Logged : Step
    }

    enum class Reason { NOT_SET_UP, OFFLINE, FAILED }
}

@HiltViewModel
internal class EatOutViewModel @Inject constructor(
    private val estimator: RestaurantEstimator,
    private val diary: DiaryRepository,
    private val session: SessionRepository,
) : ViewModel() {
    var clock: Clock = Clock.systemDefaultZone()

    private val _state = MutableStateFlow(EatOutState(meal = mealFor(LocalTime.now(clock))))
    val state: StateFlow<EatOutState> = _state

    fun onDishChange(text: String) = _state.update {
        it.copy(dish = text.take(EstimateRules.MAX_DISH_LENGTH), step = EatOutState.Step.Typing)
    }

    fun onMealChange(meal: Meal) = _state.update { it.copy(meal = meal) }

    fun onTypedKcalChange(text: String) = _state.update { it.copy(typedKcal = text.filter(Char::isDigit).take(4)) }

    fun estimate() {
        val dish = _state.value.dish.trim()
        if (dish.isEmpty()) return
        _state.update { it.copy(step = EatOutState.Step.Estimating) }
        viewModelScope.launch {
            val step = try {
                EatOutState.Step.Result(estimator.estimate(dish))
            } catch (e: EstimateUnavailable) {
                EatOutState.Step.Unavailable(
                    when (e) {
                        is EstimateUnavailable.NotSetUp -> EatOutState.Reason.NOT_SET_UP
                        is EstimateUnavailable.Offline -> EatOutState.Reason.OFFLINE
                        is EstimateUnavailable.Failed -> EatOutState.Reason.FAILED
                    },
                )
            }
            _state.update { if (it.step == EatOutState.Step.Estimating) it.copy(step = step) else it }
        }
    }

    /** Logs the typical value of the estimate, keeping the range so Today can show "≈ 450–680". */
    fun logEstimate() {
        val result = _state.value.step as? EatOutState.Step.Result ?: return
        save(result.estimate)
    }

    fun logTyped() {
        val kcal = _state.value.typedKcal.toIntOrNull()?.takeIf { it in 1..5000 } ?: return
        save(EstimateRules.typed(kcal))
    }

    private fun save(estimate: Estimate) {
        val s = _state.value
        val dish = s.dish.trim().ifEmpty { return }
        viewModelScope.launch {
            diary.save(buildEntry(session.currentUser.value.userId, dish, s.meal, estimate, clock))
            _state.update { it.copy(step = EatOutState.Step.Logged) }
        }
    }

    companion object {
        /** Breakfast before 11:00, lunch before 16:00, otherwise dinner. Eating out is rarely a snack. */
        fun mealFor(time: LocalTime): Meal = when {
            time.isBefore(LocalTime.of(11, 0)) -> Meal.BREAKFAST
            time.isBefore(LocalTime.of(16, 0)) -> Meal.LUNCH
            else -> Meal.DINNER
        }

        fun buildEntry(userId: String, dish: String, meal: Meal, estimate: Estimate, clock: Clock) = LogEntry(
            id = newId(),
            userId = userId,
            date = LocalDate.now(clock),
            meal = meal,
            what = Logged.Restaurant(dish, estimate),
            portion = Portion(grams = 0.0),
            nutrients = Nutrients(estimate.typical.toDouble(), 0.0, 0.0, 0.0),
            isEstimate = true,
            createdAt = Instant.now(clock),
        )
    }
}
