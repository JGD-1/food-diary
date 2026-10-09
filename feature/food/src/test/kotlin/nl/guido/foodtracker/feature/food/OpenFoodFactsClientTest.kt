package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.feature.food.data.BarcodeMemory
import nl.guido.foodtracker.feature.food.data.Http
import nl.guido.foodtracker.feature.food.data.HttpResponse
import nl.guido.foodtracker.feature.food.off.OpenFoodFactsClient
import nl.guido.foodtracker.feature.food.off.RateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {
    private class FakeHttp(val response: HttpResponse) : Http {
        val gets = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            gets += url to headers
            return response
        }
        override suspend fun postJson(url: String, json: String, headers: Map<String, String>) = error("not used")
    }

    private fun client(http: Http) = OpenFoodFactsClient(
        http, "https://off.test", "FoodDiary/1.0 (test@example.com)",
        RateLimiter(15, clock = { 0L }), RateLimiter(10, clock = { 0L }),
    )

    @Test fun `product lookup sends our User-Agent and asks only for needed fields`() = runTest {
        val http = FakeHttp(HttpResponse(200, """{"status":0}"""))
        assertNull(client(http).product("5449000000996"))
        val (url, headers) = http.gets.single()
        assertTrue(url.startsWith("https://off.test/api/v2/product/5449000000996?fields="))
        assertEquals("FoodDiary/1.0 (test@example.com)", headers["User-Agent"])
    }

    @Test fun `search is limited to products sold in the Netherlands`() = runTest {
        val http = FakeHttp(HttpResponse(200, """{"products":[]}"""))
        client(http).search("halfvolle melk")
        val url = http.gets.single().first
        assertTrue(url.contains("search_terms=halfvolle+melk"))
        assertTrue(url.contains("tag_0=netherlands"))
    }

    @Test fun `barcode variants and not-found memory`() {
        assertEquals(listOf("0012345678905", "012345678905"), BarcodeMemory.variants("012345678905"))
        assertEquals(listOf("5449000000996"), BarcodeMemory.variants(" 5449000000996\n"))
        assertEquals(emptyList<String>(), BarcodeMemory.variants("abc"))
        var now = 0L
        val memory = BarcodeMemory { now }
        memory.remember("1")
        assertTrue(memory.recentlyMissed("1"))
        now = 11 * 60_000L
        assertFalse(memory.recentlyMissed("1"))
    }
}
