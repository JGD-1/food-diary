package nl.guido.foodtracker.feature.today

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.data.repo.EnergyRepository

/** feature/energy binds the real EnergyRepository; until it does, Today works without it. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TodayModule {
    @BindsOptionalOf abstract fun energyRepository(): EnergyRepository
}
