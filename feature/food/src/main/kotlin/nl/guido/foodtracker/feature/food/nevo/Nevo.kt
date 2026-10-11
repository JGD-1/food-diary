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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The NEVO version bundled in assets. Change both when a new NEVO file is bundled. */
internal const val NEVO_VERSION = "2025/9.0"
internal const val NEVO_ASSET = "nevo2025_9.0.csv"
/** Bumped when the bundled file gains columns, so phones import the foods again (same ids, rows replaced). */
private const val IMPORT_KEY = "$NEVO_VERSION+fibre-sugar-sodium"

/** Both names of a NEVO food and its NEVO food group (English), for search and its ordering. */
internal data class NevoInfo(val english: String, val dutch: String, val group: String)

private val DRINK_GROUPS = setOf("Alcoholic beverages", "Non-alcoholic beverages")

/**
 * Reads the bundled NEVO table (a column subset of NEVO2025_v9.0.csv with values unchanged, as the
 * licence asks). Lines: code|group|name_nl|name_en|per|kcal|protein|carbs|fat|fibre|sugar|sodium_mg,
 * decimal comma (NEVO's FIBT, SUGAR and NA). An empty fibre, sugar or sodium value means "not known".
 * Salt is not a NEVO column: the app works it out from sodium (salt g = sodium mg × 2.5 / 1000), the usual
 * label rule, and that number is the app's own addition. Older 9-column lines still read (without these).
 * Only rows "per 100g" are used: the 53 rows "per 100ml" (infant formula, rehydration drinks) are skipped,
 * because the app counts everything per 100 g.
 */
internal object NevoParser {
    /** The foods, named in Dutch when [dutch] (a phone set to Dutch), else in English. */
    fun parse(reader: Reader, dutch: Boolean = false): List<Food> = rows(reader).map { toFood(it, dutch) }

    /** Food id to both names and group, so search finds "volkoren brood" and "kaas" whatever the phone's language. */
    fun info(reader: Reader): Map<Id, NevoInfo> = rows(reader).associate { nevoId(it[0]) to NevoInfo(it[3].ifBlank { it[2] }, it[2], it[1]) }

    private fun rows(reader: Reader): List<List<String>> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("code|") }
            .map { line -> line.split('|').also { require(it.size == 9 || it.size == 12) { "NEVO line has ${it.size} columns: $line" } } }
            .filter { it[4] == "per 100g" }
            .toList()
    }

    private fun toFood(c: List<String>, dutch: Boolean): Food {
        return Food(
            id = nevoId(c[0]),
            name = if (dutch) c[2].trim().ifBlank { c[3] } else c[3].ifBlank { c[2] },
            per100g = Nutrients(
                kcal = num(c[5]), protein = num(c[6]), carbs = num(c[7]), fat = num(c[8]),
                fibre = c.getOrNull(9)?.let(::numOrNull),
                sugar = c.getOrNull(10)?.let(::numOrNull),
                salt = c.getOrNull(11)?.let(::numOrNull)?.let(::saltFromSodiumMg),
            ),
            source = FoodOrigin.NEVO,
            isDrink = c[1] in DRINK_GROUPS,
        )
    }

    private fun num(text: String) = numOrNull(text) ?: 0.0

    private fun numOrNull(text: String): Double? = if (text.isBlank()) null else text.trim().replace(',', '.').toDouble()
}

/**
 * Puts the bundled NEVO foods into the phone's food table once per NEVO version and phone language:
 * on a phone set to Dutch the foods carry their Dutch NEVO names ("Appel m schil gem"), else the English
 * ones. Switching the language imports them again under the same ids, so the diary keeps working.
 */
@Singleton
internal class NevoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foods: FoodRepository,
) {
    private val mutex = Mutex()
    private var info: Map<Id, NevoInfo>? = null
    private val prefs by lazy { context.getSharedPreferences("food_nevo", Context.MODE_PRIVATE) }

    suspend fun ensureImported() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dutch = Locale.getDefault().language == "nl"
            val key = IMPORT_KEY + if (dutch) "+nl" else "+en"
            if (prefs.getString("version", null) == key) return@withContext
            val list = context.assets.open(NEVO_ASSET).bufferedReader().use { NevoParser.parse(it, dutch) }
            list.chunked(500).forEach { foods.saveAll(it) }
            prefs.edit().putString("version", key).apply()
        }
    }

    /** Both names and the group of every NEVO food, read once from the bundled file. */
    suspend fun info(): Map<Id, NevoInfo> = mutex.withLock {
        info ?: withContext(Dispatchers.IO) {
            context.assets.open(NEVO_ASSET).bufferedReader().use { NevoParser.info(it) }
        }.also { info = it }
    }
}

/** Salt in grams from sodium in milligrams (salt = sodium × 2.5), as food labels count it. */
internal fun saltFromSodiumMg(sodiumMg: Double): Double = sodiumMg * 2.5 / 1000.0
