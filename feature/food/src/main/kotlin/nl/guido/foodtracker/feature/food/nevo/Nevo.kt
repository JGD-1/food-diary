package nl.guido.foodtracker.feature.food.nevo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.nevoId
import java.io.Reader
import javax.inject.Inject
import javax.inject.Singleton

/** The NEVO version bundled in assets. Change both when a new NEVO file is bundled. */
internal const val NEVO_VERSION = "2025/9.0"
internal const val NEVO_ASSET = "nevo2025_9.0.csv"

private val DRINK_GROUPS = setOf("Alcoholic beverages", "Non-alcoholic beverages")

/**
 * Reads the bundled NEVO table (a column subset of NEVO2025_v9.0.csv with values unchanged, as the
 * licence asks). Lines: code|group|name_nl|name_en|per|kcal|protein|carbs|fat, decimal comma.
 * Only rows "per 100g" are used: the 53 rows "per 100ml" (infant formula, rehydration drinks) are skipped,
 * because the app counts everything per 100 g.
 */
internal object NevoParser {
    fun parse(reader: Reader): List<Food> = rows(reader).map { toFood(it) }

    /** Food id to Dutch name, so search also finds "volkoren brood" and "kaas". */
    fun dutchNames(reader: Reader): Map<Id, String> = rows(reader).associate { nevoId(it[0]) to it[2] }

    private fun rows(reader: Reader): List<List<String>> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("code|") }
            .map { line -> line.split('|').also { require(it.size == 9) { "NEVO line has ${it.size} columns: $line" } } }
            .filter { it[4] == "per 100g" }
            .toList()
    }

    private fun toFood(c: List<String>): Food {
        return Food(
            id = nevoId(c[0]),
            name = c[3].ifBlank { c[2] },
            per100g = Nutrients(kcal = num(c[5]), protein = num(c[6]), carbs = num(c[7]), fat = num(c[8])),
            source = FoodOrigin.NEVO,
            isDrink = c[1] in DRINK_GROUPS,
        )
    }

    private fun num(text: String) = if (text.isBlank()) 0.0 else text.replace(',', '.').toDouble()
}

/** Puts the bundled NEVO foods into the phone's food table once per NEVO version. */
@Singleton
internal class NevoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foods: FoodRepository,
) {
    private val mutex = Mutex()
    private var dutch: Map<Id, String>? = null
    private val prefs by lazy { context.getSharedPreferences("food_nevo", Context.MODE_PRIVATE) }

    suspend fun ensureImported() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (prefs.getString("version", null) == NEVO_VERSION) return@withContext
            val list = context.assets.open(NEVO_ASSET).bufferedReader().use { NevoParser.parse(it) }
            list.chunked(500).forEach { foods.saveAll(it) }
            prefs.edit().putString("version", NEVO_VERSION).apply()
        }
    }

    /** Dutch names of the NEVO foods, read once from the bundled file. */
    suspend fun dutchNames(): Map<Id, String> = mutex.withLock {
        dutch ?: withContext(Dispatchers.IO) {
            context.assets.open(NEVO_ASSET).bufferedReader().use { NevoParser.dutchNames(it) }
        }.also { dutch = it }
    }
}
