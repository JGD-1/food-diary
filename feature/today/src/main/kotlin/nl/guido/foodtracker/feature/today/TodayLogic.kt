package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.core.model.sum
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** The four food meals, in the order they appear on Today. Drinks have their own card. */
internal val FOOD_MEALS = listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER, Meal.SNACKS)

/** The meal that fits the time of day: the default when logging and for "+ Add dinner". */
internal fun mealForTime(time: LocalTime): Meal = when {
    time < LocalTime.of(10, 30) -> Meal.BREAKFAST
    time < LocalTime.of(15, 0) -> Meal.LUNCH
    time < LocalTime.of(21, 0) -> Meal.DINNER
    else -> Meal.SNACKS
}

/** Where a camera result goes: Drinks for a drink or a scan started from Drinks, else the meal for the time. */
internal fun mealForCameraResult(isDrink: Boolean, fromDrinks: Boolean, time: LocalTime): Meal =
    if (isDrink || fromDrinks) Meal.DRINKS else mealForTime(time)

internal data class MealSummary(val meal: Meal, val entries: List<LogEntry>) {
    val kcal: Int get() = entries.sumOf { it.nutrients.kcal }.roundToInt()
    val hasEstimate: Boolean get() = entries.any { it.isEstimate }
}

/** Everything the Today screen shows, worked out from the diary. */
internal data class TodaySummary(
    /** Null until a profile exists, so there is no target yet. */
    val targetKcal: Int?,
    val eaten: Nutrients,
    val hasEstimate: Boolean,
    /** Food meals that have something logged, in meal order. */
    val filledMeals: List<MealSummary>,
    val drinks: MealSummary,
    /** The meal offered as "+ Add dinner": the first empty one from now on. */
    val nextEmptyMeal: Meal?,
    /** Empty meals before the current one, offered as small "+ Add lunch" lines (forgot to log it). */
    val earlierEmptyMeals: List<Meal>,
    /** What was eaten the last time for each meal, so "Again" works on an empty meal too. */
    val lastTime: Map<Meal, List<LogEntry>>,
) {
    val eatenKcal: Int get() = eaten.kcal.roundToInt()
    /** Negative when over target; the screen shows that as "over target", never as a warning. */
    val kcalLeft: Int? get() = targetKcal?.let { it - eatenKcal }
    val ringFraction: Float
        get() = if (targetKcal == null || targetKcal <= 0) 0f
        else (eatenKcal.toFloat() / targetKcal).coerceIn(0f, 1f)
}

/** [currentMeal] is null for an earlier day: it is over, so every empty meal is an "earlier" one. */
internal fun todaySummary(
    todayEntries: List<LogEntry>,
    history: List<LogEntry>,
    targetKcal: Int?,
    currentMeal: Meal?,
): TodaySummary {
    val byMeal = todayEntries.groupBy { it.meal }
    val filled = FOOD_MEALS.filter { byMeal[it].orEmpty().isNotEmpty() }.map { MealSummary(it, byMeal.getValue(it)) }
    val now = if (currentMeal == null) FOOD_MEALS.size else FOOD_MEALS.indexOf(currentMeal).coerceAtLeast(0)
    val fromNow = FOOD_MEALS.drop(now)
    return TodaySummary(
        targetKcal = targetKcal,
        eaten = todayEntries.map { it.nutrients }.sum(),
        hasEstimate = todayEntries.any { it.isEstimate },
        filledMeals = filled,
        drinks = MealSummary(Meal.DRINKS, byMeal[Meal.DRINKS].orEmpty()),
        nextEmptyMeal = fromNow.firstOrNull { byMeal[it].isNullOrEmpty() },
        earlierEmptyMeals = FOOD_MEALS.take(now).filter { byMeal[it].isNullOrEmpty() },
        lastTime = lastTimeByMeal(history),
    )
}

/** For each meal, the entries of the most recent earlier day that had that meal. */
internal fun lastTimeByMeal(history: List<LogEntry>): Map<Meal, List<LogEntry>> =
    history.groupBy { it.meal }.mapValues { (_, entries) ->
        val lastDate = entries.maxOf { it.date }
        entries.filter { it.date == lastDate }.sortedBy { it.createdAt }
    }

/**
 * What "Again" logs for a meal: today's items once more when the meal already has some,
 * otherwise what was eaten the last time.
 */
internal fun againSource(summary: TodaySummary, meal: Meal): List<LogEntry> =
    mealEntries(summary, meal).ifEmpty { summary.lastTime[meal].orEmpty() }

/** The lines logged in [meal] on the day shown. */
internal fun mealEntries(summary: TodaySummary, meal: Meal): List<LogEntry> =
    if (meal == Meal.DRINKS) summary.drinks.entries
    else summary.filledMeals.firstOrNull { it.meal == meal }?.entries.orEmpty()

/** Copies diary lines to [date] as new lines, e.g. for "Again". */
internal fun copyEntries(
    entries: List<LogEntry>,
    date: LocalDate,
    now: Instant,
    meal: Meal? = null,
    idFor: () -> Id = ::newId,
): List<LogEntry> = entries.map { it.copy(id = idFor(), date = date, meal = meal ?: it.meal, createdAt = now) }

/** The same lines in another meal ("Move to…"). Ids stay, so it is a move, not a copy. */
internal fun moveEntries(entries: List<LogEntry>, to: Meal): List<LogEntry> = entries.map { it.copy(meal = to) }

/** Meals a meal's lines can move to: the other food meals (drinks stay in Drinks). */
internal fun moveTargets(from: Meal): List<Meal> = if (from == Meal.DRINKS) emptyList() else FOOD_MEALS - from

/** The day after [date], or null when that would be after [today] (no logging ahead). */
internal fun nextDay(date: LocalDate, today: LocalDate): LocalDate? = date.plusDays(1).takeIf { it <= today }

/** The date to put in a route: null for today, so plain routes keep working. */
internal fun routeDate(date: LocalDate, today: LocalDate): LocalDate? = date.takeIf { it != today }

/** Reads a route's ISO date extra; null when missing or unreadable. */
internal fun parseDate(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** True when the amount of this line can be changed (restaurant estimates have no weight). */
internal fun canChangeAmount(entry: LogEntry): Boolean =
    entry.what !is Logged.Restaurant && entry.portion.grams > 0.0

/** The same line with a new amount; nutrients scale with the weight. */
internal fun withGrams(entry: LogEntry, grams: Double): LogEntry {
    require(canChangeAmount(entry)) { "This line has no weight to change" }
    val factor = grams / entry.portion.grams
    return entry.copy(portion = entry.portion.copy(grams = grams, label = null), nutrients = entry.nutrients * factor)
}

/** Kcal per day for the energy calculation. */
internal fun dayTotals(entries: List<LogEntry>): List<DayTotal> =
    entries.groupBy { it.date }.map { (date, list) -> DayTotal(date, list.sumOf { it.nutrients.kcal }) }.sortedBy { it.date }

/** The name shown for a diary line. */
internal val LogEntry.displayName: String
    get() = when (val w = what) {
        is Logged.FoodRef -> w.name
        is Logged.RecipeRef -> w.name
        is Logged.BatchShare -> w.name
        is Logged.Restaurant -> w.dish
    }

/** Parses a typed amount ("150", "150,5", " 80 ") into grams; null when it isn't a sensible amount. */
internal fun parseGrams(text: String): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it > 0.0 && it <= 5000.0 }
}

/** Energy share of each macro (protein and carbs 4 kcal/g, fat 9 kcal/g), in whole percent. */
internal data class MacroShares(val protein: Int, val carbs: Int, val fat: Int)

internal fun macroShares(n: Nutrients): MacroShares? {
    val p = n.protein * 4
    val c = n.carbs * 4
    val f = n.fat * 9
    val total = p + c + f
    if (total <= 0.0) return null
    return MacroShares((p / total * 100).roundToInt(), (c / total * 100).roundToInt(), (f / total * 100).roundToInt())
}

/** Grams of carbs in a meal's lines (meal sheet). */
internal fun mealCarbs(entries: List<LogEntry>): Double = entries.sumOf { it.nutrients.carbs }

internal enum class ExtraNutrient { FIBRE, SUGAR, SALT }

/**
 * Fibre, sugar and salt eaten, only those that are known for at least one line: older lines and
 * foods without these numbers leave them null, so a 0 is never shown for "not known".
 */
internal fun extraNutrients(n: Nutrients): List<Pair<ExtraNutrient, Double>> = listOfNotNull(
    n.fibre?.let { ExtraNutrient.FIBRE to it },
    n.sugar?.let { ExtraNutrient.SUGAR to it },
    n.salt?.let { ExtraNutrient.SALT to it },
)

/** The pot portion that was saved with diary line [entryId]. */
internal fun portionFor(portions: List<BatchPortion>, entryId: Id): BatchPortion? =
    portions.firstOrNull { it.logEntryId == entryId }
