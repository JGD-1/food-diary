package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.ProductName
import nl.guido.foodtracker.feature.food.nevo.NevoImporter
import nl.guido.foodtracker.feature.food.nevo.NevoInfo
import nl.guido.foodtracker.feature.food.off.OpenFoodFacts
import nl.guido.foodtracker.feature.food.search.EverydayWords
import nl.guido.foodtracker.feature.food.search.EverydayWordsAsset
import nl.guido.foodtracker.feature.food.search.matchesWords
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
    /** NEVO food id to both names and group; also tells which NEVO rows the app uses. */
    private val nevoInfo: suspend () -> Map<Id, NevoInfo>,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Everyday words for NEVO foods ("havermout" → oat flakes, S2). */
    private val everydayWords: suspend () -> EverydayWords = { EverydayWords.EMPTY },
) : FoodSource {
    @Inject constructor(foods: FoodRepository, off: OpenFoodFacts, nevo: NevoImporter, everyday: EverydayWordsAsset) :
        this(foods, off, { nevo.ensureImported() }, { nevo.info() }, everydayWords = { everyday.get() }) {
        // Import NEVO in the background as soon as anything needs foods, so the first search is quick.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { nevo.ensureImported() } }
    }

    /** Barcodes Open Food Facts didn't know, so we don't ask again for a day. */
    private val unknown = mutableMapOf<String, Long>()

    /** Names Open Food Facts had for barcodes without calories, for [nameByBarcode]. */
    private val names = mutableMapOf<String, ProductName>()

    override suspend fun byBarcode(code: String): Food? {
        val barcode = code.trim()
        foods.byBarcode(barcode)?.let { return it }
        val askedAt = synchronized(unknown) { unknown[barcode] }
        if (askedAt != null && clock() - askedAt < DAY) return null
        val lookup = try {
            off.lookUp(barcode)
        } catch (e: IOException) {
            return null
        }
        val found = lookup?.food
        if (found == null) {
            synchronized(unknown) { unknown[barcode] = clock() }
            lookup?.name?.let { name -> synchronized(names) { names[barcode] = name } }
            return null
        }
        foods.save(found)
        return found
    }

    override suspend fun nameByBarcode(code: String): ProductName? = synchronized(names) { names[code.trim()] }

    override suspend fun search(text: String): List<Food> {
        val words = searchWords(text)
        if (words.isEmpty()) return emptyList()
        runCatching { ensureNevo() }
        val nevo = runCatching { nevoInfo() }.getOrDefault(emptyMap())
        // The database can match one word; the longest one narrows it down the most.
        val found = foods.search(words.maxBy { it.length }, limit = 400).first()
        // Add NEVO foods that match by either name (the table holds the one in the phone's language).
        val known = found.mapTo(HashSet()) { it.id }
        val byOtherName = nevo.filter { (id, info) -> id !in known && matchesWords(words, listOf(info.english, info.dutch)) }
            .keys.take(300).mapNotNull { foods.get(it) }
        known += byOtherName.map { it.id }
        // Foods the typed everyday word stands for, even when their NEVO name uses other words.
        val everyday = runCatching { everydayWords().foodsFor(text) }.getOrDefault(emptyList())
        val byEveryday = everyday.filter { it !in known }.mapNotNull { foods.get(it) }
        // NEVO rows the app doesn't use (per 100 ml) may still be on phones from an older version.
        val candidates = (found + byOtherName + byEveryday).filter { it.source != FoodOrigin.NEVO || nevo.isEmpty() || it.id in nevo }
        return rankFoods(text, candidates, nevo = nevo, everyday = everyday)
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
