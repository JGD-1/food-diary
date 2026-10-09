package nl.guido.foodtracker.feature.camera

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import nl.guido.foodtracker.core.model.WeightReading
import nl.guido.foodtracker.core.model.WeightSource
import javax.inject.Inject

/**
 * STAND-IN weight source that always reads 100 g. Stream 2 replaces it with the camera
 * scale reader and typed grams. All weight sources are collected in a Set<WeightSource>,
 * so a Bluetooth scale can be added later without changing anything else.
 */
internal class StandInWeightSource @Inject constructor() : WeightSource {
    override val name = "Stand-in"
    override suspend fun readGrams() = WeightReading(grams = 100.0, sourceName = name)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraModule {
    @Binds @IntoSet abstract fun standIn(impl: StandInWeightSource): WeightSource
}
