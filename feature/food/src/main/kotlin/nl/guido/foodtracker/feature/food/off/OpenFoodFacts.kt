package nl.guido.foodtracker.feature.food.off

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.net.WebClient
import nl.guido.foodtracker.feature.food.offId
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Contact for Open Food Facts: their rules ask every app to identify itself with a User-Agent. */
internal const val OFF_USER_AGENT = "FoodDiary/1.0 (cptdillinger@gmail.com)"

private const val BASE = "https://world.openfoodfacts.org"
private const val FIELDS = "code,product_name,product_name_nl,product_name_en,brands,nutriments,categories_tags," +
    "serving_quantity,serving_quantity_unit,product_quantity,product_quantity_unit"

/**
 * Open Food Facts: product lookups by barcode (at most 15 a minute, as in the plan) and an explicit
 * search (at most 10 a minute, never per keystroke). Returns null / empty when offline or over the limit.
 */
@Singleton
internal class OpenFoodFacts(
    private val web: WebClient,
    private val productLimit: RateLimiter,
    private val searchLimit: RateLimiter,
) {
    @Inject constructor(web: WebClient) : this(web, RateLimiter(15), RateLimiter(10))

    private val headers = mapOf("User-Agent" to OFF_USER_AGENT, "Accept" to "application/json")

    /** The product, or null when it isn't in Open Food Facts, has no calories, or we can't ask now. */
    suspend fun product(barcode: String): Food? {
        if (!isBarcode(barcode) || !productLimit.tryAcquire()) return null
        val response = web.get("$BASE/api/v2/product/$barcode?fields=$FIELDS", headers)
        if (response.code != 200) return null
        val root = parse(response.body) ?: return null
        if (root["status"]?.let { (it as? JsonPrimitive)?.intOrNull } != 1) return null
        val product = root["product"] as? JsonObject ?: return null
        return toFood(product, barcode)
    }

    suspend fun search(text: String): List<Food> {
        val query = text.trim()
        if (query.length < 2 || !searchLimit.tryAcquire()) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1" +
            "&page_size=20&sort_by=unique_scans_n&fields=$FIELDS"
        val response = web.get(url, headers)
        if (response.code != 200) return emptyList()
        val products = parse(response.body)?.get("products") as? JsonArray ?: return emptyList()
        return products.mapNotNull { element ->
            val p = element as? JsonObject ?: return@mapNotNull null
            val code = p.string("code") ?: return@mapNotNull null
            if (isBarcode(code)) toFood(p, code) else null
        }.distinctBy { it.id }
    }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun isBarcode(code: String) = code.length in 8..14 && code.all { it in '0'..'9' }

        /** Turns an Open Food Facts product into a food per 100 g, or null if it has no name or calories. */
        fun toFood(p: JsonObject, barcode: String): Food? {
            val name = listOf("product_name", "product_name_nl", "product_name_en")
                .firstNotNullOfOrNull { key -> p.string(key)?.trim()?.takeIf { it.isNotEmpty() } } ?: return null
            val n = p["nutriments"] as? JsonObject ?: return null
            val kcal = n.number("energy-kcal_100g") ?: n.number("energy_100g")?.let { it / 4.184 } ?: return null
            val categories = (p["categories_tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val isDrink = "en:beverages" in categories && categories.none { it == "en:dairies" || it == "en:milks" }
            return Food(
                id = offId(barcode),
                name = name,
                brand = p.string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
                barcode = barcode,
                per100g = Nutrients(
                    kcal = kcal,
                    protein = n.number("proteins_100g") ?: 0.0,
                    carbs = n.number("carbohydrates_100g") ?: 0.0,
                    fat = n.number("fat_100g") ?: 0.0,
                    fibre = n.number("fiber_100g"),
                    sugar = n.number("sugars_100g"),
                    salt = n.number("salt_100g") ?: n.number("sodium_100g")?.let { it * 2.5 },
                ),
                source = FoodOrigin.OFF,
                isDrink = isDrink,
                servingG = p.amount("serving_quantity", "serving_quantity_unit"),
                packageG = p.amount("product_quantity", "product_quantity_unit"),
            )
        }

        /**
         * A serving or pack size in grams. Open Food Facts gives these in g, or ml for drinks (counted as g,
         * like everywhere in the app); other units and silly numbers are left out.
         */
        private fun JsonObject.amount(key: String, unitKey: String): Double? {
            val unit = string(unitKey)?.trim()?.lowercase()
            if (unit != null && unit != "g" && unit != "ml") return null
            return number(key)?.takeIf { it > 0.0 && it <= 20_000.0 }
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        /** Open Food Facts sends numbers either as numbers or as text. */
        private fun JsonObject.number(key: String): Double? {
            val value: JsonElement = this[key] ?: return null
            val primitive = value as? JsonPrimitive ?: return null
            return (primitive.doubleOrNull ?: primitive.contentOrNull?.replace(',', '.')?.toDoubleOrNull())
                ?.takeIf { it.isFinite() && it >= 0.0 }
        }
    }
}
