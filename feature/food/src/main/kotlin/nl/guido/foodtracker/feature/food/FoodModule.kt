package nl.guido.foodtracker.feature.food

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.model.CommonPortions
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.dishes.DishListEstimator
import nl.guido.foodtracker.feature.food.net.UrlConnectionWebClient
import nl.guido.foodtracker.feature.food.net.WebClient
import nl.guido.foodtracker.feature.food.pieces.BundledCommonPortions

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FoodModule {
    @Binds abstract fun foodSource(impl: RealFoodSource): FoodSource
    @Binds abstract fun restaurantEstimator(impl: DishListEstimator): RestaurantEstimator
    /** Fills core/data's optional CommonPortions binding. */
    @Binds abstract fun commonPortions(impl: BundledCommonPortions): CommonPortions
    @Binds abstract fun webClient(impl: UrlConnectionWebClient): WebClient
}
