package nl.guido.foodtracker.feature.food

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.dishes.DishListEstimator
import nl.guido.foodtracker.feature.food.net.UrlConnectionWebClient
import nl.guido.foodtracker.feature.food.net.WebClient

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FoodModule {
    @Binds abstract fun foodSource(impl: RealFoodSource): FoodSource
    @Binds abstract fun restaurantEstimator(impl: DishListEstimator): RestaurantEstimator
    @Binds abstract fun webClient(impl: UrlConnectionWebClient): WebClient
}
