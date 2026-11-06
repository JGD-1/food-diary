package nl.guido.foodtracker.core.model

import java.time.LocalDate
import java.util.UUID

/**
 * One person's kcal for one day, shared with their household only when they switch on
 * "Show my day to my partner" (round 10). Nothing else from the diary is shared.
 */
data class SharedDay(
    val userId: Id,
    val date: LocalDate,
    val kcal: Int,
    /** Their own daily target; null while their profile isn't filled in. */
    val targetKcal: Int?,
    val updatedAt: Long = 0,
) {
    /** One row per person per day, the same id on every phone. */
    val id: Id get() = sharedDayId(userId, date)

    /** Share of their own target eaten (0.0 and up, not capped); null without a target. */
    val fraction: Float? get() = targetKcal?.takeIf { it > 0 }?.let { kcal.toFloat() / it }
}

fun sharedDayId(userId: Id, date: LocalDate): Id =
    UUID.nameUUIDFromBytes("day:$userId:$date".toByteArray()).toString()
