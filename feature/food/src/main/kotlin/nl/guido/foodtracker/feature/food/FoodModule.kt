package nl.guido.foodtracker.feature.food

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.estimate.EstimateCache
import nl.guido.foodtracker.feature.food.estimate.EstimateServer
import nl.guido.foodtracker.feature.food.estimate.PrefsEstimateCache
import nl.guido.foodtracker.feature.food.estimate.SupabaseRestaurantEstimator
import nl.guido.foodtracker.feature.food.net.UrlConnectionWebClient
import nl.guido.foodtracker.feature.food.net.WebClient

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FoodModule {
    @Binds abstract fun foodSource(impl: RealFoodSource): FoodSource
    @Binds abstract fun restaurantEstimator(impl: SupabaseRestaurantEstimator): RestaurantEstimator
    @Binds abstract fun webClient(impl: UrlConnectionWebClient): WebClient
    @Binds abstract fun estimateCache(impl: PrefsEstimateCache): EstimateCache

    companion object {
        @Provides fun estimateServer() = EstimateServer(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
    }
}
