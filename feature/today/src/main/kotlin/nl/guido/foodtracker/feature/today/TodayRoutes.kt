package nl.guido.foodtracker.feature.today

import android.net.Uri
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.ui.Routes
import java.time.LocalDate

/**
 * Screen names this module opens or offers.
 * Log food takes optional extras, so other screens can open it ready to go:
 * `log-food?meal=DINNER&foodId=<id>&grams=150` (all optional; plain `log-food` works too).
 */
internal object TodayRoutes {
    const val ARG_MEAL = "meal"
    const val ARG_FOOD_ID = "foodId"
    const val ARG_GRAMS = "grams"
    const val ARG_DATE = Routes.ARG_DATE
    const val LOG_FOOD_PATTERN =
        "${Routes.LOG_FOOD}?$ARG_MEAL={$ARG_MEAL}&$ARG_FOOD_ID={$ARG_FOOD_ID}&$ARG_GRAMS={$ARG_GRAMS}&$ARG_DATE={$ARG_DATE}"

    /** [date] null = today. */
    fun logFood(meal: Meal? = null, foodId: Id? = null, grams: Double? = null, date: LocalDate? = null): String {
        val args = listOfNotNull(
            meal?.let { "$ARG_MEAL=${it.name}" },
            foodId?.let { "$ARG_FOOD_ID=${Uri.encode(it)}" },
            grams?.let { "$ARG_GRAMS=$it" },
            date?.let { "$ARG_DATE=$it" },
        )
        return if (args.isEmpty()) Routes.LOG_FOOD else Routes.LOG_FOOD + "?" + args.joinToString("&")
    }

}

/**
 * Opens [route], or [fallback] when that screen isn't in the app yet (other streams are still
 * building theirs), instead of crashing. Returns false when neither exists.
 */
internal fun NavController.navigateSafely(route: String, fallback: String? = null): Boolean =
    runCatching { navigate(route) }.isSuccess ||
        (fallback != null && runCatching { navigate(fallback) }.isSuccess)
