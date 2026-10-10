package nl.guido.foodtracker.feature.food.eatout

import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.newId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** The meal you're most likely eating out at this time of day. Drinks are logged elsewhere. */
internal fun mealAt(time: LocalTime): Meal = when {
    time < LocalTime.of(10, 30) -> Meal.BREAKFAST
    time < LocalTime.of(15, 0) -> Meal.LUNCH
    time < LocalTime.of(21, 30) -> Meal.DINNER
    else -> Meal.SNACKS
}

/** Kcal typed by hand for a dish that isn't in the list, read as a whole positive number. */
internal fun typedKcal(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..5000 }

/** A typed number becomes a "range" of one value, so it's still shown as an estimate. */
internal fun typedEstimate(kcal: Int) = Estimate(kcal, kcal, kcal)

/**
 * A diary line for a restaurant dish: no weight, the typical kcal counted, macros unknown (0),
 * and always marked as an estimate so it looks different from weighed food.
 */
internal fun restaurantEntry(
    userId: Id,
    date: LocalDate,
    meal: Meal,
    dish: String,
    estimate: Estimate,
    now: Instant,
) = LogEntry(
    id = newId(),
    userId = userId,
    date = date,
    meal = meal,
    what = Logged.Restaurant(dish.trim(), estimate),
    portion = Portion(0.0),
    nutrients = Nutrients(estimate.typical.toDouble(), 0.0, 0.0, 0.0),
    isEstimate = true,
    createdAt = now,
)

/** How much of the usual restaurant portion was eaten. [mark] goes before the dish name on the diary line. */
enum class PortionSize(val factor: Double, val mark: String?) {
    HALF(0.5, "½"),
    WHOLE(1.0, null),
    ONE_AND_HALF(1.5, "1½"),
}

/** The dish's range for this portion size: low, typical and high all scaled, rounded to whole kcal. */
internal fun Estimate.scaled(size: PortionSize): Estimate =
    if (size == PortionSize.WHOLE) this
    else Estimate(scaleKcal(low, size), scaleKcal(typical, size), scaleKcal(high, size))

private fun scaleKcal(kcal: Int, size: PortionSize) = (kcal * size.factor).roundToInt()

/** "½ Pizza margherita" for half a portion; the plain name for a whole one. */
internal fun dishWithSize(dish: String, size: PortionSize): String =
    size.mark?.let { "$it ${dish.trim()}" } ?: dish.trim()

/** The day to log to, from the screen's `date` extra (ISO); today when it's missing or unreadable. */
internal fun dayFrom(arg: String?, today: LocalDate): LocalDate =
    arg?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
