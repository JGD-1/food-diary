package nl.guido.foodtracker.feature.food.estimate

import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.feature.food.search.FoodRanking

/** Small rules shared by the estimate service and the Eat out screen. */
internal object EstimateRules {
    const val MAX_DISH_LENGTH = 200

    /** The cache key for a dish: "Pizza  Margherita!" and "pizza margherita" are the same dish. */
    fun dishKey(dish: String): String = FoodRanking.fold(dish).replace(Regex("\\s+"), " ").take(MAX_DISH_LENGTH)

    /** Rejects answers that can't be a single restaurant meal. */
    fun isSane(e: Estimate): Boolean = e.low in 1..5000 && e.typical in e.low..e.high && e.high <= 5000

    /** A number the user typed themselves, kept as an estimate without a range. */
    fun typed(kcal: Int): Estimate = Estimate(kcal, kcal, kcal)
}
