package nl.guido.foodtracker.feature.recipes

import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.ui.CameraResult
import nl.guido.foodtracker.core.ui.FoodPickPurpose
import nl.guido.foodtracker.core.ui.Routes
import java.time.LocalDate

/**
 * Screens inside this module. LOG and BATCH_PORTION are the shared Routes that Log food opens
 * too; the others are only opened from here.
 */
internal object RecipesRoutes {
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BATCH_ID = "batchId"
    /** Optional: the meal chosen in Log food, so the recipe or portion lands there (else by time of day). */
    const val ARG_MEAL = Routes.ARG_MEAL
    /** Optional: the day being viewed on Today (ISO), so the line lands there. Missing = today. */
    const val ARG_DATE = Routes.ARG_DATE

    const val LIST = Routes.RECIPES
    const val EDIT = "recipes/edit?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val LOG = "${Routes.RECIPE_LOG}?$ARG_MEAL={$ARG_MEAL}&$ARG_DATE={$ARG_DATE}"
    const val NEW_BATCH = "recipes/batch?$ARG_RECIPE_ID={$ARG_RECIPE_ID}&$ARG_DATE={$ARG_DATE}"
    /** Optional on BATCH_PORTION: the fridge or freezer box the portion comes from (round 6, D3). */
    const val ARG_BOX = "box"
    const val BATCH_PORTION = "${Routes.BATCH_PORTION}?$ARG_MEAL={$ARG_MEAL}&$ARG_DATE={$ARG_DATE}&$ARG_BOX={$ARG_BOX}"

    fun edit(recipeId: Id? = null) = if (recipeId == null) "recipes/edit" else "recipes/edit?$ARG_RECIPE_ID=$recipeId"
    fun log(recipeId: Id) = Routes.recipeLog(recipeId)
    /** Cook a batch, from a recipe or not; [date] is the day being viewed (the cook date to start with). */
    fun newBatch(recipeId: Id? = null, date: LocalDate? = null): String {
        val args = listOfNotNull(recipeId?.let { "$ARG_RECIPE_ID=$it" }, date?.let { "$ARG_DATE=$it" })
        return if (args.isEmpty()) "recipes/batch" else "recipes/batch?" + args.joinToString("&")
    }
    fun batchPortion(batchId: Id) = Routes.batchPortion(batchId)
    /** A portion from a fridge or freezer box (D3). */
    fun boxPortion(batchId: Id, boxId: Id) = Routes.batchPortion(batchId) + "?$ARG_BOX=$boxId"

    /** "In the fridge" (round 6, D3): every box not eaten yet, shared in the household. */
    const val FRIDGE = "recipes/fridge"

    /** The meal passed by the opener, or null when none (or an unknown one) was given. */
    fun mealArg(value: String?): Meal? = Meal.entries.firstOrNull { it.name == value }

    /** The day passed by the opener, or [today] when none (or an unreadable one) was given. */
    fun dateArg(value: String?, today: LocalDate): LocalDate =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today

    /** Set on the screen below "Change batch" when the batch was changed, so it reloads (S10). */
    const val BATCH_CHANGED = "recipes_batch_changed"
    const val BATCH_EDIT = Routes.BATCH_EDIT
    fun batchEdit(batchId: Id) = Routes.batchEdit(batchId)

    /** "Scan a note" (round 4, H): [ARG_PURPOSE] says what the shared picker is opened for when a line's food is changed. */
    const val ARG_PURPOSE = "purpose"
    const val NOTE_SCAN = "recipes/note-scan?$ARG_PURPOSE={$ARG_PURPOSE}"
    fun noteScan(purpose: FoodPickPurpose) = "recipes/note-scan?$ARG_PURPOSE=${purpose.name}"
    /** The checked lines, handed back to the screen that opened "Scan a note" (encodeNoteIngredients). */
    const val NOTE_RESULT = "recipes_note_result"

    /** The camera's scale reader (stream 2). It hands back grams under [CAMERA_GRAMS] before closing. */
    const val CAMERA_SCALE = Routes.CAMERA_SCALE
    const val CAMERA_GRAMS = CameraResult.GRAMS
}
