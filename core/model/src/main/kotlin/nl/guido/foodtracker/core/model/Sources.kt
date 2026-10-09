package nl.guido.foodtracker.core.model

/** One weight reading in grams, and where it came from (camera, typed, later Bluetooth). */
data class WeightReading(val grams: Double, val sourceName: String)

/** Anything that can tell us how many grams are on the scale. Bluetooth scales plug in here later. */
interface WeightSource {
    val name: String
    suspend fun readGrams(): WeightReading
}

/** A place to find foods: Open Food Facts, NEVO, our own foods. */
interface FoodSource {
    suspend fun byBarcode(code: String): Food?
    suspend fun search(text: String): List<Food>

    /** Search that may use the internet (Open Food Facts). Only on an explicit search, never per keystroke. */
    suspend fun searchOnline(text: String): List<Food> = emptyList()
}

/** Gives a kcal range for a restaurant dish described in words. Swappable provider. */
interface RestaurantEstimator {
    suspend fun estimate(dish: String): Estimate
}

/** How the daily target was calculated, shown on the "how is my target calculated" screen. */
data class TargetBreakdown(
    val bmrKcal: Int,
    val activityFactor: Double,
    val maintenanceKcal: Int,
    val deficitKcal: Int,
    /** Gradual correction learned from weekly weigh-ins (positive = eat more). */
    val adjustmentKcal: Int,
    val targetKcal: Int,
    val isManual: Boolean,
)

interface EnergyEstimator {
    fun dailyTarget(profile: UserProfile, weighIns: List<WeighIn>, intake: List<DayTotal>): TargetBreakdown
}
