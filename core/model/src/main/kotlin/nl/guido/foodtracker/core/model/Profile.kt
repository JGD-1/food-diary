package nl.guido.foodtracker.core.model

import java.time.LocalDate

enum class Sex { FEMALE, MALE }

/** Multiplier on resting energy use (BMR). */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}

data class UserProfile(
    val id: Id,
    val name: String,
    val birthYear: Int,
    val sex: Sex,
    val heightCm: Int,
    val activity: ActivityLevel,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val weeklyPaceKg: Double,
    /** Set when the user types their own daily target; wins over the calculation. */
    val manualTargetKcal: Int? = null,
    /** Optional daily protein goal in grams, shown next to the macros. Null = no goal. */
    val proteinGoalG: Int? = null,
)

data class WeighIn(val id: Id, val userId: Id, val date: LocalDate, val kg: Double)
