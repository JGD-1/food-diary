package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.feature.food.data.Http
import nl.guido.foodtracker.feature.food.data.HttpResponse
import nl.guido.foodtracker.feature.food.estimate.EstimateCache
import nl.guido.foodtracker.feature.food.estimate.EstimateRules
import nl.guido.foodtracker.feature.food.estimate.EstimateUnavailable
import nl.guido.foodtracker.feature.food.estimate.ServerRestaurantEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class EstimateTest {
    private class MemoryCache : EstimateCache {
        val map = mutableMapOf<String, Estimate>()
        override fun get(key: String) = map[key]
        override fun put(key: String, estimate: Estimate) { map[key] = estimate }
    }

    private class FakeHttp(var answer: () -> HttpResponse) : Http {
        val posts = mutableListOf<Triple<String, String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>) = error("not used")
        override suspend fun postJson(url: String, json: String, headers: Map<String, String>): HttpResponse {
            posts += Triple(url, json, headers)
            return answer()
        }
    }

    @Test fun `dish key ignores case, accents, punctuation and spacing`() {
        assertEquals("pizza margherita", EstimateRules.dishKey("  Pizza   Margherita! "))
        assertEquals(EstimateRules.dishKey("Crêpe"), EstimateRules.dishKey("crepe"))
    }

    @Test fun `sanity check`() {
        assertTrue(EstimateRules.isSane(Estimate(450, 560, 680)))
        assertFalse(EstimateRules.isSane(Estimate(600, 500, 680)))
        assertFalse(EstimateRules.isSane(Estimate(0, 0, 0)))
        assertFalse(EstimateRules.isSane(Estimate(450, 560, 9000)))
    }

    @Test fun `asks the server once, then answers from the phone cache`() = runTest {
        val http = FakeHttp { HttpResponse(200, """{"low":450,"typical":560,"high":680,"cached":false}""") }
        val estimator = ServerRestaurantEstimator(http, MemoryCache(), "https://x.supabase.co/", "anon")
        assertEquals(Estimate(450, 560, 680), estimator.estimate("Pizza margherita"))
        assertEquals(Estimate(450, 560, 680), estimator.estimate("pizza  MARGHERITA"))
        assertEquals(1, http.posts.size)
        val (url, body, headers) = http.posts.single()
        assertEquals("https://x.supabase.co/functions/v1/estimate", url)
        assertEquals("""{"dish":"Pizza margherita"}""", body)
        assertEquals("Bearer anon", headers["Authorization"])
    }

    @Test fun `not set up, offline and bad answers are reported, not cached`() = runTest {
        val cache = MemoryCache()
        expect<EstimateUnavailable.NotSetUp> {
            ServerRestaurantEstimator(FakeHttp { error("no call") }, cache, "", "").estimate("soup")
        }
        expect<EstimateUnavailable.Offline> {
            ServerRestaurantEstimator(FakeHttp { throw IOException("down") }, cache, "https://x", "k").estimate("soup")
        }
        expect<EstimateUnavailable.Failed> {
            ServerRestaurantEstimator(FakeHttp { HttpResponse(429, "") }, cache, "https://x", "k").estimate("soup")
        }
        expect<EstimateUnavailable.Failed> {
            ServerRestaurantEstimator(FakeHttp { HttpResponse(200, """{"low":9,"typical":5,"high":1}""") }, cache, "https://x", "k")
                .estimate("soup")
        }
        assertTrue(cache.map.isEmpty())
    }

    private suspend inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
            fail("expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }
}
