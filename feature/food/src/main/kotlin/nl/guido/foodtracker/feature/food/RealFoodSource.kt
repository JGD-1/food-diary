package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.feature.food.nevo.NevoImporter
import nl.guido.foodtracker.feature.food.off.OpenFoodFacts
import nl.guido.foodtracker.feature.food.search.rankFoods
import nl.guido.foodtracker.feature.food.search.searchWords
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foods for search and barcodes. Search is local only (my foods, cached Open Food Facts, NEVO);
 * the internet is used only for a barcode we don't know yet and for the explicit "Search online".
 */
@Singleton
internal class RealFoodSource(
    private val foods: FoodRepository,
    private val off: OpenFoodFacts,
    /** Makes sure NEVO is in the food table; a plain function so tests need no Android. */
    private val ensureNevo: suspend () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : FoodSource {
    @Inject constructor(foods: FoodRepository, off: OpenFoodFacts, nevo: NevoImporter) :
        this(foods, off, { nevo.ensureImported() }) {
        // Import NEVO in the background as soon as anything needs foods, so the first search is quick.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { nevo.ensureImported() } }
    }

    /** Barcodes Open Food Facts didn't know, so we don't ask again for a day. */
    private val unknown = mutableMapOf<String, Long>()

    override suspend fun byBarcode(code: String): Food? {
        val barcode = code.trim()
        foods.byBarcode(barcode)?.let { return it }
        val askedAt = synchronized(unknown) { unknown[barcode] }
        if (askedAt != null && clock() - askedAt < DAY) return null
        val found = try {
            off.product(barcode)
        } catch (e: IOException) {
            return null
        }
        if (found == null) {
            synchronized(unknown) { unknown[barcode] = clock() }
            return null
        }
        foods.save(found)
        return found
    }

    override suspend fun search(text: String): List<Food> {
        val words = searchWords(text)
        if (words.isEmpty()) return emptyList()
        runCatching { ensureNevo() }
        // The database can match one word; the longest one narrows it down the most.
        val candidates = foods.search(words.maxBy { it.length }, limit = 400).first()
        return rankFoods(text, candidates)
    }

    override suspend fun searchOnline(text: String): List<Food> {
        val found = try {
            off.search(text)
        } catch (e: IOException) {
            return emptyList()
        }
        // Save them, so the food can be logged by id and found offline next time.
        if (found.isNotEmpty()) foods.saveAll(found)
        return found
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
