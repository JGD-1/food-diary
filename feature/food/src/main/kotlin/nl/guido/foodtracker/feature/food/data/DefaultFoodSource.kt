package nl.guido.foodtracker.feature.food.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.feature.food.nevo.NevoImporter
import nl.guido.foodtracker.feature.food.off.OpenFoodFactsClient
import nl.guido.foodtracker.feature.food.search.Candidate
import nl.guido.foodtracker.feature.food.search.FoodRanking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds foods. Everything already on the phone (own foods, label scans, NEVO, products scanned
 * before) is searched instantly and offline. Open Food Facts is asked only for an unknown
 * barcode, or when the user taps "Search online".
 */
@Singleton
internal class DefaultFoodSource @Inject constructor(
    private val foods: FoodRepository,
    private val nevo: NevoImporter,
    private val off: OpenFoodFactsClient,
) : FoodSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notFound = BarcodeMemory()

    init {
        // Import NEVO in the background as soon as anything needs food data.
        scope.launch { nevo.ready() }
    }

    /**
     * The product for a barcode: from the phone if seen before, else from Open Food Facts (then kept
     * on the phone). Null when unknown or when there's no internet, so the camera shows its alternatives.
     */
    override suspend fun byBarcode(code: String): Food? {
        val variants = BarcodeMemory.variants(code)
        if (variants.isEmpty()) return null
        variants.forEach { v -> foods.byBarcode(v)?.let { return it } }
        if (notFound.recentlyMissed(variants.first())) return null
        return try {
            val found = off.product(variants.first())
            if (found == null) notFound.remember(variants.first()) else foods.save(found)
            found
        } catch (e: IOException) {
            Log.i(TAG, "Open Food Facts not reachable: ${e.message}")
            null
        }
    }

    /** Foods on the phone matching every typed word, in English or (for NEVO) Dutch. Offline and instant. */
    override suspend fun search(text: String): List<Food> {
        val probe = FoodRanking.probe(text) ?: return emptyList()
        val index = nevo.ready()
        val local = foods.search(probe, limit = 200).first()
        val dutch = index.matches(text).mapNotNull { code -> foods.get(FoodIds.nevo(code)) }
        val candidates = (local + dutch).map { food ->
            Candidate(food, listOfNotNull(if (food.source == FoodOrigin.NEVO) nevo.dutchName(food.id) else null))
        }
        return FoodRanking.rank(text, candidates)
    }

    /**
     * Searches Open Food Facts (products sold in the Netherlands). Only on an explicit tap:
     * they allow 10 searches a minute. Results are kept on the phone. Throws [IOException] when offline.
     */
    suspend fun searchOnline(text: String): List<Food> {
        if (FoodRanking.probe(text) == null) return emptyList()
        val found = off.search(text)
        foods.saveAll(found)
        return found
    }

    private companion object {
        const val TAG = "FoodSource"
    }
}
