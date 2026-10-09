package nl.guido.foodtracker.feature.today

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/** Line icons from the design (24 × 24, round ends). */
internal object TodayIcons {
    val Add = lineIcon("Add", "M12 5v14M5 12h14", width = 2.4f)
    val Scan = lineIcon("Scan", "M4 7V5h3M17 5h3v2M20 17v2h-3M7 19H4v-2M8 9v6M11 9v6M14 9v6M17 9v6")
    val Weigh = lineIcon(
        "Weigh",
        "M5 10h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z",
        "M7 10V7h10v3M10 15h4",
    )
    val EatOut = lineIcon("EatOut", "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 2-2 6 0 8v10")
    val Search = lineIcon("Search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4")
    val Back = lineIcon("Back", "M15 5l-7 7 7 7")

    private fun lineIcon(name: String, vararg paths: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}

internal fun formatKcal(kcal: Int): String = NumberFormat.getIntegerInstance().format(kcal)

internal fun formatKcal(kcal: Double): String = formatKcal(kcal.roundToInt())

/** "200", "12.5": whole numbers without decimals. */
internal fun formatAmount(grams: Double): String {
    val rounded = (grams * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** "−0.4", "+0.2" (a true minus sign, easier to read). */
internal fun formatChange(kg: Double): String = when {
    kg < 0 -> "−" + formatAmount(abs(kg))
    kg > 0 -> "+" + formatAmount(kg)
    else -> "0"
}

@StringRes
internal fun mealName(meal: Meal): Int = when (meal) {
    Meal.BREAKFAST -> R.string.today_meal_breakfast
    Meal.LUNCH -> R.string.today_meal_lunch
    Meal.DINNER -> R.string.today_meal_dinner
    Meal.SNACKS -> R.string.today_meal_snacks
    Meal.DRINKS -> R.string.today_meal_drinks
}

@StringRes
internal fun addMeal(meal: Meal): Int = when (meal) {
    Meal.BREAKFAST -> R.string.today_add_breakfast
    Meal.LUNCH -> R.string.today_add_lunch
    Meal.DINNER -> R.string.today_add_dinner
    Meal.SNACKS -> R.string.today_add_snacks
    Meal.DRINKS -> R.string.today_add_drink
}

@StringRes
internal fun addToMeal(meal: Meal): Int = when (meal) {
    Meal.BREAKFAST -> R.string.today_add_to_breakfast
    Meal.LUNCH -> R.string.today_add_to_lunch
    Meal.DINNER -> R.string.today_add_to_dinner
    Meal.SNACKS -> R.string.today_add_to_snacks
    Meal.DRINKS -> R.string.today_add_to_drinks
}

/** "Greek yoghurt 200 g" or "Club sandwich · restaurant, 450–680". */
@Composable
internal fun entryText(entry: LogEntry): String {
    val what = entry.what
    if (what is Logged.Restaurant) {
        return stringResource(
            R.string.today_restaurant_item, what.dish,
            formatKcal(what.estimate.low), formatKcal(what.estimate.high),
        )
    }
    return "${entry.displayName} ${amountText(entry)}"
}

@Composable
internal fun amountText(entry: LogEntry): String = entry.portion.label
    ?: stringResource(
        if (entry.meal == Meal.DRINKS) R.string.today_amount_ml else R.string.today_amount_grams,
        formatAmount(entry.portion.grams),
    )

@Composable
internal fun entriesText(entries: List<LogEntry>): String = entries.map { entryText(it) }.joinToString(", ")

/** "380", or "≈ 560" when an estimate is part of it. */
@Composable
internal fun kcalText(kcal: Int, estimate: Boolean): String =
    stringResource(if (estimate) R.string.today_kcal_estimate else R.string.today_kcal_value, formatKcal(kcal))

/**
 * Picks up what the camera left behind (a scanned food and/or a scale weight) when it
 * returns to [entry], hands it to [onResult] once, and clears it.
 */
@Composable
internal fun CameraResults(entry: NavBackStackEntry, onResult: (foodId: String?, grams: Double?) -> Unit) {
    val handle = entry.savedStateHandle
    val foodId by handle.getStateFlow<String?>(TodayRoutes.RESULT_FOOD_ID, null).collectAsState()
    val grams by handle.getStateFlow<Double?>(TodayRoutes.RESULT_GRAMS, null).collectAsState()
    LaunchedEffect(foodId, grams) {
        if (foodId != null || grams != null) {
            onResult(foodId, grams)
            handle.remove<String>(TodayRoutes.RESULT_FOOD_ID)
            handle.remove<Double>(TodayRoutes.RESULT_GRAMS)
        }
    }
}
