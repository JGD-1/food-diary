package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import java.time.LocalDate

/** What the profile form holds while someone is typing. Plain text, checked by [toProfile]. */
internal data class ProfileForm(
    val birthYear: String = "",
    val sex: Sex? = null,
    val heightCm: String = "",
    /** Only asked the first time; after that weight comes from weigh-ins. */
    val weightKg: String = "",
    val activity: ActivityLevel? = null,
    val targetWeightKg: String = "",
    val weeklyPaceKg: Double = DEFAULT_PACE,
    val useOwnTarget: Boolean = false,
    val ownTargetKcal: String = "",
    /** Optional daily protein goal in grams; empty = no goal. */
    val proteinGoalG: String = "",
) {
    /** The profile to save, or null while something is missing or out of range. */
    fun toProfile(userId: String, name: String, today: LocalDate, existing: UserProfile?): UserProfile? {
        val year = birthYear.trim().toIntOrNull()?.takeIf { it in (today.year - 100)..(today.year - 14) } ?: return null
        val height = heightCm.trim().toIntOrNull()?.takeIf { it in 100..250 } ?: return null
        val start = existing?.startWeightKg ?: parseKg(weightKg) ?: return null
        val target = parseKg(targetWeightKg) ?: return null
        val protein = proteinGoalG.trim().ifEmpty { null }?.let { it.toIntOrNull()?.takeIf { g -> g in 10..400 } ?: return null }
        val own = if (useOwnTarget) ownTargetKcal.trim().toIntOrNull()?.takeIf { it in 800..6000 } ?: return null else null
        return UserProfile(
            id = userId,
            name = existing?.name ?: name,
            birthYear = year,
            sex = sex ?: return null,
            heightCm = height,
            activity = activity ?: return null,
            startWeightKg = start,
            targetWeightKg = target,
            weeklyPaceKg = weeklyPaceKg,
            manualTargetKcal = own,
            proteinGoalG = protein,
        )
    }

    companion object {
        const val DEFAULT_PACE = 0.5
        val PACES = listOf(0.25, 0.5, 0.75, 1.0)

        fun from(profile: UserProfile?): ProfileForm = if (profile == null) ProfileForm() else ProfileForm(
            birthYear = profile.birthYear.toString(),
            sex = profile.sex,
            heightCm = profile.heightCm.toString(),
            weightKg = formatKg(profile.startWeightKg),
            activity = profile.activity,
            targetWeightKg = formatKg(profile.targetWeightKg),
            weeklyPaceKg = profile.weeklyPaceKg,
            useOwnTarget = profile.manualTargetKcal != null,
            ownTargetKcal = profile.manualTargetKcal?.toString().orEmpty(),
            proteinGoalG = profile.proteinGoalG?.toString().orEmpty(),
        )
    }
}

/** "84,2" or "84.2" → 84.2, for body weights between 30 and 300 kg. */
internal fun parseKg(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 30.0..300.0 }

internal fun formatKg(kg: Double): String = String.format("%.1f", kg)
