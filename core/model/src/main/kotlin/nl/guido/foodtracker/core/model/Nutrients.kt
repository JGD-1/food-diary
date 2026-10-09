package nl.guido.foodtracker.core.model

import kotlinx.serialization.Serializable

/** Energy and macros. On a [Food] this is per 100 g; on a [LogEntry] it is the total for the portion. */
@Serializable
data class Nutrients(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
) {
    operator fun plus(other: Nutrients) =
        Nutrients(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat)

    operator fun times(factor: Double) =
        Nutrients(kcal * factor, protein * factor, carbs * factor, fat * factor)

    /** Totals for [grams] of a food whose values are per 100 g. */
    fun forGrams(grams: Double): Nutrients = this * (grams / 100.0)

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0)
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }
