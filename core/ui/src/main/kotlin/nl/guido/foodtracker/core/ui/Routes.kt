package nl.guido.foodtracker.core.ui

import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Names of all screens, so any screen can open any other with navController.navigate(Routes.X).
 * The owning stream builds the screen; adding a route here goes through the lead.
 */
object Routes {
    // Optional extras. Screens that take them register these names in their own route pattern,
    // e.g. "log-food?meal={meal}&date={date}"; a plain "log-food" keeps working.
    /** ISO day (2026-10-10) the screen logs to. Missing = today. */
    const val ARG_DATE = "date"
    const val ARG_MEAL = "meal"
    /** Eat out: the typed words to fill in. */
    const val ARG_DISH = "dish"
    const val ARG_FOOD_ID = "foodId"
    /** Weigh-in to change (weigh-in?id=). Missing = a new weigh-in. */
    const val ARG_ID = "id"

    // Bottom tabs
    const val TODAY = "today"
    const val WEIGHT = "weight"
    const val STATS = "stats"

    const val LOG_FOOD = "log-food"          // stream 5
    const val EAT_OUT = "eat-out"            // stream 1
    const val CAMERA = "camera"              // stream 2
    const val CAMERA_SCALE = "camera?mode=scale"
    const val CAMERA_DRINK = "camera?drink=true"
    const val RECIPES = "recipes"            // stream 3
    const val RECIPE_LOG = "recipe-log/{recipeId}"
    const val BATCH_PORTION = "batch-portion/{batchId}"
    const val ENERGY_TARGET = "energy-target" // stream 4
    const val WEIGH_IN = "weigh-in"          // stream 4 (opened from the Weight tab)
    const val PROFILE = "profile"            // stream 4 (profile) + 7 (account, export)
    const val SIGN_IN = "sign-in"            // stream 7
    const val DAY = "day/{$ARG_DATE}"        // Today for another day (opened from Stats); P1 registers it
    const val FOOD_EDIT = "food-edit/{$ARG_FOOD_ID}" // P2 (camera's food form, pre-filled)
    const val REMINDERS = "reminders"        // P5 (feature/reminders)

    fun recipeLog(recipeId: Id, meal: Meal? = null, date: LocalDate? = null) =
        withArgs("recipe-log/$recipeId", ARG_MEAL to meal?.name, ARG_DATE to date?.toString())

    fun batchPortion(batchId: Id, meal: Meal? = null, date: LocalDate? = null) =
        withArgs("batch-portion/$batchId", ARG_MEAL to meal?.name, ARG_DATE to date?.toString())

    fun eatOut(date: LocalDate? = null, dish: String? = null) =
        withArgs(EAT_OUT, ARG_DATE to date?.toString(), ARG_DISH to dish?.takeIf { it.isNotBlank() })

    fun logFood(meal: Meal? = null, date: LocalDate? = null) =
        withArgs(LOG_FOOD, ARG_MEAL to meal?.name, ARG_DATE to date?.toString())

    fun day(date: LocalDate) = "day/$date"
    fun weighIn(id: Id? = null) = withArgs(WEIGH_IN, ARG_ID to id)
    fun foodEdit(foodId: Id) = "food-edit/${encode(foodId)}"

    /** Adds the extras that are set as "?a=1&b=2", text safely encoded. */
    fun withArgs(route: String, vararg args: Pair<String, String?>): String {
        val set = args.filter { it.second != null }
        if (set.isEmpty()) return route
        val joiner = if ('?' in route) "&" else "?"
        return route + joiner + set.joinToString("&") { (k, v) -> "$k=${encode(v!!)}" }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

/**
 * Notifications open the app on a screen by putting its route in the launch intent under [EXTRA_ROUTE];
 * MainActivity navigates there (e.g. the lunch reminder opens Routes.logFood(Meal.LUNCH)).
 */
object OpenScreen {
    const val EXTRA_ROUTE = "nl.guido.foodtracker.open_route"
}

/** Keys the camera screen uses to hand its result back (previousBackStackEntry.savedStateHandle). */
object CameraResult {
    const val FOOD_ID = "camera_food_id"
    const val GRAMS = "camera_grams"
}
