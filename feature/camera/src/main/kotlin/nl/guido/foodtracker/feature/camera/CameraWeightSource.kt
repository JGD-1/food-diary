package nl.guido.foodtracker.feature.camera

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import nl.guido.foodtracker.core.model.WeightReading
import nl.guido.foodtracker.core.model.WeightSource
import javax.inject.Inject
import javax.inject.Singleton

/** Steady readings from the camera's scale reader, while the scale view is on screen. */
@Singleton
internal class CameraScaleReadings @Inject constructor() {
    private val readings = MutableSharedFlow<Double>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun publish(grams: Double) { readings.tryEmit(grams) }

    suspend fun next(): Double = readings.first()
}

/**
 * The kitchen scale read through the camera. It is one of the app's weight sources
 * (a Set<WeightSource>): the scale view listens to all of them, so a Bluetooth scale
 * added later shows up there without changes.
 */
internal class CameraScaleWeightSource @Inject constructor(
    private val readings: CameraScaleReadings,
) : WeightSource {
    override val name = "Camera"
    override suspend fun readGrams() = WeightReading(grams = readings.next(), sourceName = name)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraModule {
    @Binds @IntoSet abstract fun cameraScale(impl: CameraScaleWeightSource): WeightSource
}
