package nl.guido.foodtracker.core.model

import java.time.LocalTime

/**
 * The one meal-by-time rule of the whole app (Round 17, N1): Log food, the camera, Koken, Uit eten and the
 * reminders all use it, so the same moment never lands in two different meals.
 * Before 10:30 breakfast, before 15:00 lunch, before 22:00 dinner (late dinners stay dinner), then snacks.
 */
fun mealForTime(time: LocalTime): Meal = when {
    time < LocalTime.of(10, 30) -> Meal.BREAKFAST
    time < LocalTime.of(15, 0) -> Meal.LUNCH
    time < LocalTime.of(22, 0) -> Meal.DINNER
    else -> Meal.SNACKS
}
