package nl.guido.foodtracker.feature.food.usda

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.nevo.NevoInfo
import nl.guido.foodtracker.feature.food.nevo.saltFromSodiumMg
import java.io.Reader
import java.util.UUID

/** The bundled vegetables NEVO lacks (Round 15), from USDA FoodData Central (public domain). */
internal const val USDA_ASSET = "usda_vegetables.csv"

/** Name-based id, like NEVO's, so both phones give the same food the same id. */
internal fun usdaId(fdcId: String): Id = UUID.nameUUIDFromBytes("usda:$fdcId".toByteArray()).toString()

/**
 * Reads [USDA_ASSET]. Lines: fdc_id|group|name_nl|name_en|kcal|protein|carbs_total|fat|fibre|sugar|sodium_mg,
 * decimal comma. USDA's carbohydrate includes fibre; the app counts carbs without it (as NEVO and EU labels),
 * so carbs = carbs_total − fibre. Everything else as USDA publishes it; salt from sodium as for NEVO.
 */
internal object UsdaParser {
    fun parse(reader: Reader, dutch: Boolean = false): List<Food> = rows(reader).map { c ->
        val fibre = num(c[8])
        Food(
            id = usdaId(c[0]),
            name = if (dutch) c[2] else c[3],
            per100g = Nutrients(
                kcal = num(c[4]), protein = num(c[5]), carbs = (num(c[6]) - fibre).coerceAtLeast(0.0), fat = num(c[7]),
                fibre = fibre, sugar = num(c[9]), salt = saltFromSodiumMg(num(c[10])),
            ),
            source = FoodOrigin.USDA,
        )
    }

    /** Both names and the group, for search (the same shape as NEVO's). */
    fun info(reader: Reader): Map<Id, NevoInfo> = rows(reader).associate { usdaId(it[0]) to NevoInfo(it[3], it[2], it[1]) }

    private fun rows(reader: Reader): List<List<String>> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("fdc_id|") }
            .map { line -> line.split('|').also { require(it.size == 11) { "USDA line has ${it.size} columns: $line" } } }
            .toList()
    }

    private fun num(text: String) = text.trim().replace(',', '.').toDouble()
}
