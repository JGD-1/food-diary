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
 */
internal object NevoParser {
    fun parse(reader: Reader): List<Food> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("code|") }
            .map { line -> parseLine(line) }
            .toList()
    }

    private fun parseLine(line: String): Food {
        val c = line.split('|')
        require(c.size == 9) { "NEVO line has ${c.size} columns: $line" }
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
    private val prefs by lazy { context.getSharedPreferences("food_nevo", Context.MODE_PRIVATE) }

    suspend fun ensureImported() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (prefs.getString("version", null) == NEVO_VERSION) return@withContext
            val list = context.assets.open(NEVO_ASSET).bufferedReader().use { NevoParser.parse(it) }
            list.chunked(500).forEach { foods.saveAll(it) }
            prefs.edit().putString("version", NEVO_VERSION).apply()
        }
    }
}
