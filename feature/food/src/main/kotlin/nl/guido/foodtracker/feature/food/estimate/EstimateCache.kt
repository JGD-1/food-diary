package nl.guido.foodtracker.feature.food.estimate

import nl.guido.foodtracker.core.model.Estimate

/** Remembers estimates on the phone, so asking for the same dish twice is instant and free. */
internal interface EstimateCache {
    fun get(key: String): Estimate?
    fun put(key: String, estimate: Estimate)
}
