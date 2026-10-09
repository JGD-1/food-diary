package nl.guido.foodtracker.feature.food.off

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.feature.food.BuildConfig
import nl.guido.foodtracker.feature.food.data.Http
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to Open Food Facts, following their rules: our own User-Agent with a contact address,
 * at most 15 product reads and 10 searches a minute, and searches only when the user asks.
 */
@Singleton
internal class OpenFoodFactsClient(
    private val http: Http,
    private val baseUrl: String,
    private val userAgent: String,
    private val productLimiter: RateLimiter = RateLimiter(maxCalls = 15),
    private val searchLimiter: RateLimiter = RateLimiter(maxCalls = 10),
) {
    @Inject constructor(http: Http) : this(http, BASE_URL, USER_AGENT)

    private val headers get() = mapOf("User-Agent" to userAgent, "Accept" to "application/json")

    /** The product, null when Open Food Facts doesn't know it. Throws [IOException] when offline. */
    suspend fun product(barcode: String): Food? = productLimiter.run {
        val url = "$baseUrl/api/v2/product/${enc(barcode)}?fields=${OpenFoodFactsParser.FIELDS}"
        val response = http.get(url, headers)
        when (response.code) {
            200 -> OpenFoodFactsParser.parseProduct(response.body, barcode)
            404 -> null
            else -> throw IOException("Open Food Facts answered ${response.code}")
        }
    }

    /** Products sold in the Netherlands whose name matches, most scanned first. */
    suspend fun search(text: String, pageSize: Int = 20): List<Food> = searchLimiter.run {
        val url = "$baseUrl/cgi/search.pl?search_terms=${enc(text)}&search_simple=1&json=1" +
            "&tagtype_0=countries&tag_contains_0=contains&tag_0=netherlands" +
            "&sort_by=unique_scans_n&page_size=$pageSize&fields=${OpenFoodFactsParser.FIELDS}"
        val response = http.get(url, headers)
        if (response.code != 200) throw IOException("Open Food Facts answered ${response.code}")
        OpenFoodFactsParser.parseSearch(response.body)
    }

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        val USER_AGENT = "FoodDiary/1.0 (${BuildConfig.OFF_CONTACT})"
    }
}
