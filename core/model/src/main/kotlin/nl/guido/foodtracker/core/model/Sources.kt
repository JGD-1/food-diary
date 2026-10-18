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

    /**
     * Name and brand the food database has for a barcode without calories, so a label scan can start
     * with them. Only what the last [byBarcode] learned: never asks the internet itself.
     */
    suspend fun nameByBarcode(code: String): ProductName? = null
}

/** A product's name and brand without its nutrition (e.g. an Open Food Facts product nobody filled in yet). */
data class ProductName(val name: String, val brand: String?)

/**
 * Common pieces for a food, e.g. Portion(150.0, "1 apple"), shown as amount chips (finding 2).
 * Bound by feature/food; optional, so callers inject Optional<CommonPortions>.
 */
interface CommonPortions {
    /** Empty when there are no common pieces for this food. */
    suspend fun forFood(food: Food): List<Portion>
}

/**
 * The app's own readable name for a food (S1), e.g. "Apple, with skin, average" for a NEVO name with
 * short forms. Screens show it as the main name and the unchanged NEVO name ([Food.name]) as the small
 * line under it; NEVO names themselves are never changed (licence), the readable name is the app's addition.
 * Bound by feature/food; optional, so callers inject Optional<FoodNames>.
 */
interface FoodNames {
    /** Null when the name needs no rewording; then show [Food.name] alone. */
    fun plain(food: Food): String?
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
