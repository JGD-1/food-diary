package nl.guido.foodtracker.feature.food

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.RestaurantEstimator
import javax.inject.Inject

/** STAND-IN: a few fixed foods so other streams can build against [FoodSource]. Stream 1 replaces it. */
internal class StandInFoodSource @Inject constructor() : FoodSource {
    private val foods = listOf(
        Food("standin-oats", "Oats", per100g = Nutrients(370.0, 13.0, 60.0, 7.0), source = FoodOrigin.MANUAL),
        Food("standin-yoghurt", "Greek yoghurt", per100g = Nutrients(97.0, 9.0, 4.0, 5.0), source = FoodOrigin.MANUAL),
        Food(
            "standin-cola", "Cola", barcode = "5449000000996",
            per100g = Nutrients(42.0, 0.0, 10.6, 0.0), source = FoodOrigin.MANUAL, isDrink = true,
        ),
    )

    override suspend fun byBarcode(code: String) = foods.firstOrNull { it.barcode == code }
    override suspend fun search(text: String) = foods.filter { it.name.contains(text, ignoreCase = true) }
}

/** STAND-IN: always the same range. Stream 1 replaces it with the real estimate service. */
internal class StandInRestaurantEstimator @Inject constructor() : RestaurantEstimator {
    override suspend fun estimate(dish: String) = Estimate(low = 450, typical = 560, high = 680)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FoodModule {
    @Binds abstract fun foodSource(impl: StandInFoodSource): FoodSource
    @Binds abstract fun restaurantEstimator(impl: StandInRestaurantEstimator): RestaurantEstimator
}
