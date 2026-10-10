package nl.guido.foodtracker.core.model

import kotlinx.serialization.Serializable

/**
 * Energy and macros. On a [Food] this is per 100 g; on a [LogEntry] it is the total for the portion.
 * [fibre], [sugar] and [salt] (grams) are optional: null means "not known", so older rows and
 * foods without these numbers still add up. A sum is null only when every part is unknown.
 */
@Serializable
data class Nutrients(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double? = null,
    val sugar: Double? = null,
    val salt: Double? = null,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat,
        fibre.plusOrNull(other.fibre), sugar.plusOrNull(other.sugar), salt.plusOrNull(other.salt),
    )

    operator fun times(factor: Double) = Nutrients(
        kcal * factor, protein * factor, carbs * factor, fat * factor,
        fibre?.times(factor), sugar?.times(factor), salt?.times(factor),
    )

    /** Totals for [grams] of a food whose values are per 100 g. */
    fun forGrams(grams: Double): Nutrients = this * (grams / 100.0)

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0)
    }
}

private fun Double?.plusOrNull(other: Double?): Double? =
    if (this == null && other == null) null else (this ?: 0.0) + (other ?: 0.0)

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }
