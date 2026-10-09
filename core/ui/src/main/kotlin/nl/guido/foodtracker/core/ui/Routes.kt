package nl.guido.foodtracker.core.ui

/**
 * Names of all screens, so any screen can open any other with navController.navigate(Routes.X).
 * The owning stream builds the screen; adding a route here goes through the lead.
 */
object Routes {
    // Bottom tabs
    const val TODAY = "today"
    const val WEIGHT = "weight"
    const val STATS = "stats"

    const val LOG_FOOD = "log-food"          // stream 5
    const val EAT_OUT = "eat-out"            // stream 1
    const val CAMERA = "camera"              // stream 2
    const val RECIPES = "recipes"            // stream 3
    const val ENERGY_TARGET = "energy-target" // stream 4
    const val PROFILE = "profile"            // stream 4 (profile) + 7 (account, export)
    const val SIGN_IN = "sign-in"            // stream 7
}
