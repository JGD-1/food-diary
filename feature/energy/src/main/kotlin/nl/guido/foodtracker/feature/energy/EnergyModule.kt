package nl.guido.foodtracker.feature.energy

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.data.repo.EnergyRepository
import nl.guido.foodtracker.core.model.EnergyEstimator
import java.time.LocalDate

@Module
@InstallIn(SingletonComponent::class)
internal object EnergyModule {
    @Provides fun today(): Today = Today { LocalDate.now() }

    @Provides fun adaptiveEnergyEstimator(today: Today) = AdaptiveEnergyEstimator(today)

    @Provides fun energyEstimator(impl: AdaptiveEnergyEstimator): EnergyEstimator = impl

    @Provides fun energyRepository(impl: EnergyData): EnergyRepository = impl
}
