package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import java.time.Instant
import java.time.LocalDate

internal val TODAY: LocalDate = LocalDate.of(2026, 10, 12) // a Monday

private var counter = 0

internal fun line(
    name: String,
    kcal: Double,
    meal: Meal = Meal.BREAKFAST,
    date: LocalDate = TODAY,
    grams: Double = 100.0,
    foodId: String = name.lowercase(),
): LogEntry = LogEntry(
    id = "id-${counter++}",
    userId = "me",
    date = date,
    meal = meal,
    what = Logged.FoodRef(foodId, name),
    portion = Portion(grams),
    nutrients = Nutrients(kcal, kcal * 0.05, kcal * 0.1, kcal * 0.02),
    isEstimate = false,
    createdAt = Instant.ofEpochSecond(date.toEpochDay() * 86_400 + counter),
)

internal fun restaurant(dish: String, typical: Int, meal: Meal = Meal.LUNCH, date: LocalDate = TODAY): LogEntry = LogEntry(
    id = "id-${counter++}",
    userId = "me",
    date = date,
    meal = meal,
    what = Logged.Restaurant(dish, Estimate(typical - 100, typical, typical + 100)),
    portion = Portion(0.0),
    nutrients = Nutrients(typical.toDouble(), 0.0, 0.0, 0.0),
    isEstimate = true,
    createdAt = Instant.ofEpochSecond(date.toEpochDay() * 86_400 + counter),
)
