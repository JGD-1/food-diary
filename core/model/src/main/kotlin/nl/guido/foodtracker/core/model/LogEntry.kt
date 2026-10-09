package nl.guido.foodtracker.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACKS, DRINKS }

/** What a diary line refers to. */
@Serializable
sealed interface Logged {
    @Serializable @SerialName("food")
    data class FoodRef(val foodId: Id, val name: String) : Logged

    /** A recipe, optionally a saved variant, plus one-off extras that don't change the recipe. */
    @Serializable @SerialName("recipe")
    data class RecipeRef(
        val recipeId: Id,
        val name: String,
        val variantId: Id? = null,
        val extras: List<Ingredient> = emptyList(),
    ) : Logged

    /** My weighed portion of a batch meal. */
    @Serializable @SerialName("batch")
    data class BatchShare(val batchId: Id, val name: String) : Logged

    @Serializable @SerialName("restaurant")
    data class Restaurant(val dish: String, val estimate: Estimate) : Logged
}

/** One line in someone's diary. [nutrients] is the total for [portion], copied in when logged. */
data class LogEntry(
    val id: Id,
    val userId: Id,
    val date: LocalDate,
    val meal: Meal,
    val what: Logged,
    val portion: Portion,
    val nutrients: Nutrients,
    val isEstimate: Boolean,
    val createdAt: Instant,
)

/** Total eaten on one day, used by energy and stats. */
data class DayTotal(val date: LocalDate, val kcal: Double)
