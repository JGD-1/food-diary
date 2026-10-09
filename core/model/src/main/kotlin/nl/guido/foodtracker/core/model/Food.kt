package nl.guido.foodtracker.core.model

import kotlinx.serialization.Serializable

/** Where a food's numbers come from. Shown to the user, and NEVO rows must stay unchanged (licence). */
enum class FoodOrigin { NEVO, OFF, LABEL, MANUAL }

/** A food or drink. Drinks are foods with [isDrink] = true, logged in [Meal.DRINKS]. */
data class Food(
    val id: Id,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100g: Nutrients,
    val source: FoodOrigin,
    val isDrink: Boolean = false,
    /** Who created it (label scans, manual foods). Null for NEVO and Open Food Facts rows. */
    val ownerId: Id? = null,
)

/** An amount of something. [label] is a friendly name such as "1 bowl" or "330 ml". */
@Serializable
data class Portion(val grams: Double, val label: String? = null)

/** A restaurant estimate in kcal, shown as a range ("450–680") and marked as an estimate. */
@Serializable
data class Estimate(val low: Int, val typical: Int, val high: Int)
