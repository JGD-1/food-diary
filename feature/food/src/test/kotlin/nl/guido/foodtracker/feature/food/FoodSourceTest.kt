package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.net.WebResponse
import nl.guido.foodtracker.feature.food.off.OpenFoodFacts
import nl.guido.foodtracker.feature.food.search.rankFoods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSourceTest {
    private val product = """{"status":1,"product":{"product_name":"Hagelslag","nutriments":{"energy-kcal_100g":480}}}"""

    @Test fun barcodeFromThePhoneNeedsNoInternet() = runTest {
        val own = food("My granola", FoodOrigin.LABEL, barcode = "12345678")
        val web = FakeWeb()
        val source = RealFoodSource(FakeFoods(own), OpenFoodFacts(web), {}, { emptyMap() })
        assertEquals(own, source.byBarcode("12345678"))
        assertTrue(web.urls.isEmpty())
    }

    @Test fun barcodeFromOpenFoodFactsIsCached() = runTest {
        val foods = FakeFoods()
        val web = FakeWeb { WebResponse(200, product) }
        val source = RealFoodSource(foods, OpenFoodFacts(web), {}, { emptyMap() })
        assertEquals("Hagelslag", source.byBarcode("8710400000001")?.name)
        assertEquals("Hagelslag", source.byBarcode("8710400000001")?.name)
        assertEquals(1, web.urls.size)
        assertEquals("Hagelslag", foods.byBarcode("8710400000001")?.name)
    }

    @Test fun unknownBarcodeIsNotAskedAgainSoon() = runTest {
        var now = 0L
        val web = FakeWeb { WebResponse(404, "") }
        val source = RealFoodSource(FakeFoods(), OpenFoodFacts(web), {}, { emptyMap() }, clock = { now })
        assertNull(source.byBarcode("12345678"))
        assertNull(source.byBarcode("12345678"))
        assertEquals(1, web.urls.size)
        now = 25 * 60 * 60 * 1000L
        source.byBarcode("12345678")
        assertEquals(2, web.urls.size)
    }

    @Test fun offlineBarcodeGivesNull() = runTest {
        val web = FakeWeb().apply { offline = true }
        assertNull(RealFoodSource(FakeFoods(), OpenFoodFacts(web), {}, { emptyMap() }).byBarcode("12345678"))
    }

    @Test fun searchIsLocalAndMatchesWordsInAnyOrder() = runTest {
        val web = FakeWeb()
        var nevoChecked = false
        val source = RealFoodSource(
            FakeFoods(food("Bread brown"), food("Bread white"), food("Brownie")),
            OpenFoodFacts(web),
            { nevoChecked = true },
            { emptyMap() },
        )
        assertEquals(listOf("Bread brown"), source.search("brown bread").map { it.name })
        assertTrue(nevoChecked)
        assertTrue(web.urls.isEmpty())
        assertTrue(source.search("  ").isEmpty())
    }

    @Test fun searchFindsNevoFoodsByDutchName() = runTest {
        val bread = food("Bread wholemeal")
        val cheese = food("Cheese Gouda 48+")
        val formula = food("Infant formula")
        val dutch = mapOf(bread.id to "Brood volkoren", cheese.id to "Kaas Goudse 48+")
        val source = RealFoodSource(FakeFoods(bread, cheese, formula), OpenFoodFacts(FakeWeb()), {}, { dutch })
        assertEquals(listOf("Bread wholemeal"), source.search("volkoren brood").map { it.name })
        assertEquals(listOf("Cheese Gouda 48+"), source.search("kaas").map { it.name })
        // NEVO rows the app no longer uses (per 100 ml) stay hidden.
        assertTrue(source.search("formula").isEmpty())
    }

    @Test fun searchOnlineSavesResults() = runTest {
        val body = """{"products":[{"code":"5449000000996","product_name":"Coca-Cola","nutriments":{"energy-kcal_100g":42}}]}"""
        val foods = FakeFoods()
        val source = RealFoodSource(foods, OpenFoodFacts(FakeWeb { WebResponse(200, body) }), {}, { emptyMap() })
        assertEquals(1, source.searchOnline("cola").size)
        assertEquals("Coca-Cola", foods.get(offId("5449000000996"))?.name)
    }

    @Test fun bestMatchesFirst() {
        val ranked = rankFoods(
            "apple",
            listOf(
                food("Pie apple"),
                food("Apple juice", FoodOrigin.OFF),
                food("Apple"),
                food("Apple raw"),
                food("Apple", FoodOrigin.LABEL),
            ),
        )
        assertEquals(
            listOf("Apple" to FoodOrigin.LABEL, "Apple" to FoodOrigin.NEVO, "Apple juice" to FoodOrigin.OFF, "Apple raw" to FoodOrigin.NEVO, "Pie apple" to FoodOrigin.NEVO),
            ranked.map { it.name to it.source },
        )
    }
}
