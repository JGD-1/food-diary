package nl.guido.foodtracker.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * One bit of sport someone typed in (round 11): what they did and the kcal it burned, as they
 * guessed or read it. No minutes or formulas. Private to each person, like the diary.
 * That day's target goes up by the day's sport kcal ([SportEntry.dayTarget]).
 */
data class SportEntry(
    val id: Id,
    val userId: Id,
    val date: LocalDate,
    /** What they did, e.g. "Hardlopen". */
    val name: String,
    val kcal: Int,
    val createdAt: Instant,
) {
    companion object {
        /** Highest kcal one entry may have; the sport screen keeps typed numbers between 1 and this. */
        const val MAX_KCAL = 3000

        /** The day's target with sport: the own target plus all sport kcal of that day; null without a target. */
        fun dayTarget(targetKcal: Int?, sportKcal: Int): Int? = targetKcal?.let { it + sportKcal.coerceAtLeast(0) }
    }
}
