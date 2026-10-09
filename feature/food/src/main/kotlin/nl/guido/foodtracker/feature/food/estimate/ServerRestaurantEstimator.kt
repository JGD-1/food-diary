package nl.guido.foodtracker.feature.food.estimate

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.BuildConfig
import nl.guido.foodtracker.feature.food.data.Http
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Why an estimate couldn't be made. The Eat out screen offers "type it yourself" for each. */
internal sealed class EstimateUnavailable(message: String) : Exception(message) {
    /** The online service isn't set up yet (no Supabase address in this build). */
    class NotSetUp : EstimateUnavailable("estimate service not set up")
    class Offline(cause: IOException) : EstimateUnavailable("offline: ${cause.message}")
    class Failed(code: Int) : EstimateUnavailable("service answered $code")
}

/**
 * Asks our own "estimate" function on Supabase (supabase/functions/estimate). That function holds
 * the AI key, keeps a shared cache per dish, and can switch AI provider without an app update.
 * Answers are also cached on the phone.
 */
@Singleton
internal class ServerRestaurantEstimator(
    private val http: Http,
    private val cache: EstimateCache,
    private val supabaseUrl: String,
    private val anonKey: String,
) : RestaurantEstimator {
    @Inject constructor(http: Http, cache: EstimateCache) :
        this(http, cache, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun estimate(dish: String): Estimate {
        val key = EstimateRules.dishKey(dish)
        require(key.isNotEmpty()) { "empty dish" }
        cache.get(key)?.let { return it }
        if (supabaseUrl.isBlank() || anonKey.isBlank()) throw EstimateUnavailable.NotSetUp()

        val response = try {
            http.postJson(
                url = "${supabaseUrl.trimEnd('/')}/functions/v1/estimate",
                json = json.encodeToString(Request(dish.trim().take(EstimateRules.MAX_DISH_LENGTH))),
                headers = mapOf("Authorization" to "Bearer $anonKey", "apikey" to anonKey),
            )
        } catch (e: IOException) {
            throw EstimateUnavailable.Offline(e)
        }
        if (response.code != 200) throw EstimateUnavailable.Failed(response.code)
        val answer = runCatching { json.decodeFromString<Answer>(response.body) }.getOrNull()
            ?: throw EstimateUnavailable.Failed(response.code)
        val estimate = Estimate(answer.low, answer.typical, answer.high)
        if (!EstimateRules.isSane(estimate)) throw EstimateUnavailable.Failed(response.code)
        cache.put(key, estimate)
        return estimate
    }

    @Serializable private data class Request(val dish: String)
    @Serializable private data class Answer(val low: Int, val typical: Int, val high: Int)
}
