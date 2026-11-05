package nl.guido.foodtracker.feature.food.off

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.offId
import nl.guido.foodtracker.feature.food.search.matchesWords
import nl.guido.foodtracker.feature.food.search.searchWords
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject

internal const val OFF_ASSET = "off_nl.tsv.gz"

/** The bundled list, or null when this build has none yet. */
private fun openAsset(context: Context): InputStream? = try {
    GZIPInputStream(context.assets.open(OFF_ASSET))
} catch (e: IOException) {
    null
}

/**
 * Dutch and Belgian shop products from Open Food Facts, bundled in the app (Round 9), so most barcodes
 * are found without internet. Made by feature/food/tools/off/make_snapshot.py; lines are
 * barcode|name|brand|kcal|protein|carbs|fat|fibre|sugar|salt|serving_g|package_g|drink, sorted by barcode.
 * The file is read from start to end on each use (a fraction of a second) instead of kept in memory,
 * and only the foods someone scans or picks are saved to the phone's food table.
 */
internal class BundledProducts(private val open: () -> InputStream?) {
    @Inject constructor(@ApplicationContext context: Context) :
        this({ openAsset(context) })

    /** The product, also when it is listed with or without a leading 0 (UPC-A and EAN-13 are the same code). */
    suspend fun byBarcode(barcode: String): Food? = withContext(Dispatchers.IO) {
        val codes = barcodeForms(barcode)
        lines { seq -> seq.firstOrNull { it.substringBefore('|') in codes } }?.let(::parse)
            ?.copy(id = offId(barcode), barcode = barcode)
    }

    /** Products whose name or brand holds every typed word, at most [limit]. */
    suspend fun search(text: String, limit: Int = 30): List<Food> = withContext(Dispatchers.IO) {
        val words = searchWords(text)
        if (words.isEmpty()) return@withContext emptyList()
        lines { seq ->
            seq.filter { line -> words.all { line.contains(it, ignoreCase = true) } }
                .mapNotNull(::parse)
                .filter { matchesWords(words, listOfNotNull(it.name, it.brand)) }
                .take(limit).toList()
        }.orEmpty()
    }

    private fun <T> lines(block: (Sequence<String>) -> T): T? = try {
        open()?.bufferedReader()?.use { reader: BufferedReader ->
            block(reader.lineSequence().filter { it.isNotEmpty() && it[0].isDigit() })
        }
    } catch (e: IOException) {
        null
    }

    companion object {
        fun barcodeForms(code: String): Set<String> = setOfNotNull(
            code,
            if (code.length == 12) "0$code" else null,
            if (code.length == 13 && code.startsWith('0')) code.drop(1) else null,
        )

        /** One line of the bundled file as a food, with the same id as the same product looked up online. */
        fun parse(line: String): Food? {
            val c = line.split('|')
            if (c.size != 13) return null
            fun num(i: Int) = c[i].toDoubleOrNull()
            return Food(
                id = offId(c[0]),
                name = c[1],
                brand = c[2].ifBlank { null },
                barcode = c[0],
                per100g = Nutrients(
                    kcal = num(3) ?: return null,
                    protein = num(4) ?: 0.0,
                    carbs = num(5) ?: 0.0,
                    fat = num(6) ?: 0.0,
                    fibre = num(7),
                    sugar = num(8),
                    salt = num(9),
                ),
                source = FoodOrigin.OFF,
                isDrink = c[12] == "1",
                servingG = num(10),
                packageG = num(11),
            )
        }
    }
}
