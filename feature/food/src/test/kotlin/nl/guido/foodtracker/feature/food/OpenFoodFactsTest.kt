package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.net.WebResponse
import nl.guido.foodtracker.feature.food.off.OFF_USER_AGENT
import nl.guido.foodtracker.feature.food.off.OpenFoodFacts
import nl.guido.foodtracker.feature.food.off.RateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsTest {
    private val cola = """
        {"status":1,"product":{"code":"5449000000996","product_name":"Coca-Cola","brands":"Coca-Cola, The Coca-Cola Company",
        "categories_tags":["en:beverages","en:carbonated-drinks"],
        "nutriments":{"energy-kcal_100g":42,"proteins_100g":0,"carbohydrates_100g":"10.6","fat_100g":0}}}
    """.trimIndent()

    @Test fun readsAProductWithUserAgent() = runTest {
        val web = FakeWeb { WebResponse(200, cola) }
        val food = OpenFoodFacts(web).product("5449000000996")!!
        assertEquals("Coca-Cola", food.name)
        assertEquals("Coca-Cola", food.brand)
        assertEquals(42.0, food.per100g.kcal, 0.001)
        assertEquals(10.6, food.per100g.carbs, 0.001)
        assertEquals(FoodOrigin.OFF, food.source)
        assertTrue(food.isDrink)
        assertEquals(offId("5449000000996"), food.id)
        assertEquals(OFF_USER_AGENT, web.headers.single()["User-Agent"])
        assertTrue(web.urls.single().startsWith("https://world.openfoodfacts.org/api/v2/product/5449000000996?"))
    }

    @Test fun kilojoulesOnlyAreTurnedIntoKcal() = runTest {
        val body = """{"status":1,"product":{"product_name":"Oats","nutriments":{"energy_100g":1548}}}"""
        val food = OpenFoodFacts(FakeWeb { WebResponse(200, body) }).product("87654321")!!
        assertEquals(370.0, food.per100g.kcal, 0.1)
        assertFalse(food.isDrink)
    }

    @Test fun fibreSugarSaltAndSizes() = runTest {
        val body = """{"status":1,"product":{"product_name":"Muesli","serving_quantity":"45","serving_quantity_unit":"g",
            "product_quantity":750,"product_quantity_unit":"g",
            "nutriments":{"energy-kcal_100g":370,"fiber_100g":8.5,"sugars_100g":"12,1","sodium_100g":0.2}}}"""
        val food = OpenFoodFacts(FakeWeb { WebResponse(200, body) }).product("87654321")!!
        assertEquals(8.5, food.per100g.fibre!!, 1e-9)
        assertEquals(12.1, food.per100g.sugar!!, 1e-9)
        assertEquals(0.5, food.per100g.salt!!, 1e-9)
        assertEquals(45.0, food.servingG!!, 0.0)
        assertEquals(750.0, food.packageG!!, 0.0)
    }

    @Test fun missingNumbersStayUnknown() = runTest {
        val food = OpenFoodFacts(FakeWeb { WebResponse(200, cola) }).product("5449000000996")!!
        assertNull(food.per100g.fibre)
        assertNull(food.per100g.salt)
        assertNull(food.servingG)
        val oddUnit = """{"status":1,"product":{"product_name":"Tea","product_quantity":"20","product_quantity_unit":"pieces",
            "nutriments":{"energy-kcal_100g":1,"salt_100g":0.01}}}"""
        val tea = OpenFoodFacts(FakeWeb { WebResponse(200, oddUnit) }).product("87654321")!!
        assertNull(tea.packageG)
        assertEquals(0.01, tea.per100g.salt!!, 1e-9)
    }

    @Test fun unknownOrEmptyProductsGiveNull() = runTest {
        assertNull(OpenFoodFacts(FakeWeb { WebResponse(404, """{"status":0}""") }).product("12345678"))
        val noKcal = """{"status":1,"product":{"product_name":"Mystery","nutriments":{}}}"""
        assertNull(OpenFoodFacts(FakeWeb { WebResponse(200, noKcal) }).product("12345678"))
    }

    @Test fun notABarcodeIsNeverSent() = runTest {
        val web = FakeWeb()
        assertNull(OpenFoodFacts(web).product("12ab"))
        assertTrue(web.urls.isEmpty())
    }

    @Test fun staysUnderTheProductLimit() = runTest {
        val web = FakeWeb { WebResponse(200, cola) }
        val off = OpenFoodFacts(web, RateLimiter(2, clock = { 0L }), RateLimiter(10))
        off.product("5449000000996"); off.product("5449000000996"); off.product("5449000000996")
        assertEquals(2, web.urls.size)
    }

    @Test fun searchKeepsProductsWithCalories() = runTest {
        val body = """{"products":[
            {"code":"5449000000996","product_name":"Coca-Cola","nutriments":{"energy-kcal_100g":42}},
            {"code":"1234","product_name":"Bad code","nutriments":{"energy-kcal_100g":1}},
            {"code":"87654321","product_name":"No kcal","nutriments":{}}]}"""
        val web = FakeWeb { WebResponse(200, body) }
        val found = OpenFoodFacts(web).search("cola light")
        assertEquals(listOf("Coca-Cola"), found.map { it.name })
        assertTrue(web.urls.single().contains("search_terms=cola+light"))
    }
}

class RateLimiterTest {
    @Test fun windowSlides() {
        var now = 0L
        val limit = RateLimiter(2, 60_000) { now }
        assertTrue(limit.tryAcquire())
        assertTrue(limit.tryAcquire())
        assertFalse(limit.tryAcquire())
        now = 60_000
        assertTrue(limit.tryAcquire())
    }
}
