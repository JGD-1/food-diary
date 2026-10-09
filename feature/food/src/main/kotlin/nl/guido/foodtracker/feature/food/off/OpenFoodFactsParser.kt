package nl.guido.foodtracker.feature.food.off

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.data.FoodIds

/** Turns Open Food Facts answers into [Food]s. Products without a name or energy value are skipped. */
internal object OpenFoodFactsParser {
    private val json = Json { ignoreUnknownKeys = true }
    private const val KJ_PER_KCAL = 4.184

    /** Fields we ask for, so answers stay small. */
    const val FIELDS = "code,product_name,product_name_nl,product_name_en,generic_name,brands,nutriments,categories_tags"

    /** Answer of /api/v2/product/<code>. Null when the product is unknown or has no usable numbers. */
    fun parseProduct(body: String, barcode: String): Food? {
        val root = json.parseToJsonElement(body).jsonObject
        if (root.int("status") != 1) return null
        val product = root["product"] as? JsonObject ?: return null
        return toFood(product, barcode)
    }

    /** Answer of /cgi/search.pl?json=1. */
    fun parseSearch(body: String): List<Food> {
        val root = json.parseToJsonElement(body).jsonObject
        val products = root["products"] as? JsonArray ?: return emptyList()
        return products.mapNotNull { element ->
            val product = element as? JsonObject ?: return@mapNotNull null
            val code = product.string("code") ?: return@mapNotNull null
            toFood(product, code)
        }
    }

    private fun toFood(product: JsonObject, barcode: String): Food? {
        val name = listOf("product_name_nl", "product_name", "product_name_en", "generic_name")
            .firstNotNullOfOrNull { product.string(it) } ?: return null
        val n = product["nutriments"] as? JsonObject ?: return null
        val kcal = n.number("energy-kcal_100g")
            ?: n.number("energy-kj_100g")?.let { it / KJ_PER_KCAL }
            ?: n.number("energy_100g")?.let { it / KJ_PER_KCAL } // "energy" is in kJ
            ?: return null
        val categories = (product["categories_tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        return Food(
            id = FoodIds.openFoodFacts(barcode),
            name = name,
            brand = product.string("brands")?.substringBefore(',')?.trim()?.ifEmpty { null },
            barcode = barcode,
            per100g = Nutrients(
                kcal = kcal,
                protein = n.number("proteins_100g") ?: 0.0,
                carbs = n.number("carbohydrates_100g") ?: 0.0,
                fat = n.number("fat_100g") ?: 0.0,
            ),
            source = FoodOrigin.OFF,
            isDrink = categories.any { it == "en:beverages" || it == "en:alcoholic-beverages" },
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    /** OFF sometimes sends numbers as text ("12.5"), sometimes with a comma. */
    private fun JsonObject.number(key: String): Double? {
        val value: JsonElement = this[key] ?: return null
        val p = value as? JsonPrimitive ?: return null
        return (p.doubleOrNull ?: p.content.replace(',', '.').toDoubleOrNull())?.takeIf { it.isFinite() && it >= 0 }
    }
}
