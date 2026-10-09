package nl.guido.foodtracker.feature.food.estimate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.net.WebClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Same rule as the server (supabase/functions/estimate): lower case, words only, single spaces. */
internal fun dishKey(dish: String): String =
    dish.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Why there's no estimate, so the screen can say something useful. */
class EstimateFailure(val reason: Reason) : Exception(reason.name) {
    enum class Reason { OFFLINE, NOT_SET_UP, FAILED }
}

/** Estimates already received on this phone, so the same dish never needs the internet twice. */
internal interface EstimateCache {
    fun get(key: String): Estimate?
    fun put(key: String, estimate: Estimate)
}

internal class PrefsEstimateCache @Inject constructor(@ApplicationContext context: Context) : EstimateCache {
    private val prefs = context.getSharedPreferences("food_estimates", Context.MODE_PRIVATE)

    override fun get(key: String): Estimate? {
        val parts = prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
        return if (parts.size == 3) Estimate(parts[0], parts[1], parts[2]) else null
    }

    override fun put(key: String, estimate: Estimate) {
        prefs.edit().putString(key, "${estimate.low},${estimate.typical},${estimate.high}").apply()
    }
}

/** Where the estimate service lives: the Supabase project's URL and public (anon) key. */
data class EstimateServer(val url: String, val anonKey: String)

@Serializable
private data class EstimateRequest(val dish: String)

/**
 * Asks our Supabase function "estimate" (which asks Claude, text only, and keeps a shared cache).
 * The provider sits behind [RestaurantEstimator], so it can be swapped without touching screens.
 */
@Singleton
internal class SupabaseRestaurantEstimator @Inject constructor(
    private val server: EstimateServer,
    private val web: WebClient,
    private val cache: EstimateCache,
) : RestaurantEstimator {
    override suspend fun estimate(dish: String): Estimate {
        val key = dishKey(dish)
        require(key.isNotEmpty()) { "Empty dish" }
        cache.get(key)?.let { return it }
        if (server.url.isBlank()) throw EstimateFailure(EstimateFailure.Reason.NOT_SET_UP)
        val response = try {
            web.postJson(
                "${server.url}/functions/v1/estimate",
                mapOf("apikey" to server.anonKey, "Authorization" to "Bearer ${server.anonKey}"),
                json.encodeToString(EstimateRequest.serializer(), EstimateRequest(dish.trim())),
            )
        } catch (e: IOException) {
            throw EstimateFailure(EstimateFailure.Reason.OFFLINE)
        }
        val estimate = when (response.code) {
            200 -> parse(response.body)
            503 -> throw EstimateFailure(EstimateFailure.Reason.NOT_SET_UP)
            else -> null
        } ?: throw EstimateFailure(EstimateFailure.Reason.FAILED)
        cache.put(key, estimate)
        return estimate
    }

    private fun parse(body: String): Estimate? = runCatching {
        json.decodeFromString(Estimate.serializer(), body)
    }.getOrNull()?.takeIf { it.low in 0..it.typical && it.typical <= it.high }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
