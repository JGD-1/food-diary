package nl.guido.foodtracker.feature.energy

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.EnergyEstimator
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.TargetBreakdown
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.Year
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * STAND-IN: plain Mifflin-St Jeor × activity, minus the deficit for the chosen pace, no
 * adjustment from weigh-ins yet. Stream 4 replaces it with the full version.
 */
internal class StandInEnergyEstimator @Inject constructor() : EnergyEstimator {
    override fun dailyTarget(profile: UserProfile, weighIns: List<WeighIn>, intake: List<DayTotal>): TargetBreakdown {
        val weight = weighIns.maxByOrNull { it.date }?.kg ?: profile.startWeightKg
        val age = Year.now().value - profile.birthYear
        val sexTerm = if (profile.sex == Sex.MALE) 5 else -161
        val bmr = (10 * weight + 6.25 * profile.heightCm - 5 * age + sexTerm).roundToInt()
        val maintenance = (bmr * profile.activity.factor).roundToInt()
        val deficit = (profile.weeklyPaceKg * 7700 / 7).roundToInt()
        val target = profile.manualTargetKcal ?: (maintenance - deficit)
        return TargetBreakdown(
            bmrKcal = bmr, activityFactor = profile.activity.factor, maintenanceKcal = maintenance,
            deficitKcal = deficit, adjustmentKcal = 0, targetKcal = target,
            isManual = profile.manualTargetKcal != null,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EnergyModule {
    @Binds abstract fun energyEstimator(impl: StandInEnergyEstimator): EnergyEstimator
}
