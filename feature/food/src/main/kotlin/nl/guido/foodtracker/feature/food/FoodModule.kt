package nl.guido.foodtracker.feature.food

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.data.DefaultFoodSource
import nl.guido.foodtracker.feature.food.data.Http
import nl.guido.foodtracker.feature.food.data.UrlConnectionHttp
import nl.guido.foodtracker.feature.food.estimate.EstimateCache
import nl.guido.foodtracker.feature.food.estimate.PrefsEstimateCache
import nl.guido.foodtracker.feature.food.estimate.ServerRestaurantEstimator

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FoodModule {
    @Binds abstract fun foodSource(impl: DefaultFoodSource): FoodSource
    @Binds abstract fun restaurantEstimator(impl: ServerRestaurantEstimator): RestaurantEstimator
    @Binds abstract fun http(impl: UrlConnectionHttp): Http
    @Binds abstract fun estimateCache(impl: PrefsEstimateCache): EstimateCache
}
