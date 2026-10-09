package nl.guido.foodtracker.feature.energy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import nl.guido.foodtracker.core.data.repo.CurrentUser
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.TargetBreakdown
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import nl.guido.foodtracker.core.model.newId
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Everything about the current user's energy target, kept up to date as they log and weigh in. */
internal data class EnergyState(
    val user: CurrentUser,
    val profile: UserProfile?,
    val weighIns: List<WeighIn>,
    /** Null until the profile is filled in. */
    val details: EnergyDetails?,
    val review: WeeklyReview?,
) {
    val target: TargetBreakdown? get() = details?.breakdown
}

/**
 * Reads profile, weigh-ins and diary for the current user and works out the target and the
 * Monday review. Today (stream 5) gets the same numbers once the lead adds a shared interface for it.
 */
@Singleton
internal class EnergyData @Inject constructor(
    private val session: SessionRepository,
    private val profiles: ProfileRepository,
    private val diary: DiaryRepository,
    private val estimator: AdaptiveEnergyEstimator,
    private val today: Today,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<EnergyState> = session.currentUser.flatMapLatest { user ->
        combine(profiles.profile(user.userId), profiles.weighIns(user.userId)) { profile, weighIns -> profile to weighIns }
            .flatMapLatest { (profile, weighIns) ->
                val date = today.date()
                // The whole history since the first weigh-in: learning replays every week from the start.
                val from = (weighIns.minOfOrNull { it.date } ?: date).minusDays(7)
                diary.entriesBetween(user.userId, from, date).map { entries ->
                    val intake = dayTotals(entries)
                    val details = profile?.let { estimator.details(it, weighIns, intake) }
                    val review = if (profile != null && details != null) {
                        WeeklyReviews.review(date, profile, weighIns, intake, details.breakdown.targetKcal)
                    } else {
                        null
                    }
                    EnergyState(user, profile, weighIns, details, review)
                }
            }
    }

    suspend fun saveProfile(profile: UserProfile, isNew: Boolean) {
        profiles.save(profile)
        // The starting weight is also the first weigh-in, so the Weight tab has a starting point.
        if (isNew) saveWeighIn(profile.startWeightKg)
    }

    /** Saves a weigh-in for today. One per week: a second one in the same week replaces the first. */
    suspend fun saveWeighIn(kg: Double): WeighIn {
        val user = session.currentUser.value
        val date = today.date()
        val existing = profiles.weighIns(user.userId).first()
            .filter { EnergyMath.weekStart(it.date) == EnergyMath.weekStart(date) }
            .maxByOrNull { it.date }
        val weighIn = WeighIn(id = existing?.id ?: newId(), userId = user.userId, date = date, kg = kg)
        profiles.saveWeighIn(weighIn)
        return weighIn
    }
}
