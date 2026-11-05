package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.net.WebResponse
import nl.guido.foodtracker.feature.food.off.BundledProducts
import nl.guido.foodtracker.feature.food.off.OpenFoodFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class BundledProductsTest {
    private val file = """
        # Product data from Open Food Facts (openfoodfacts.org), ODbL
        barcode|name|brand|kcal|protein|carbs|fat|fibre|sugar|salt|serving_g|package_g|drink
        012345678905|Peanut butter|Calvé|620|25|12|50|6||1.2|15|350|0
        5449000000996|Cola|Coca-Cola|43.02|0|10.6|0||10.6|0||330|1
        8710400000001|Hagelslag puur|De Ruijter|480|5|70|20||65|0.03|15|400|0
    """.trimIndent()

    private fun gz(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
    }.toByteArray()

    private val bundled = BundledProducts { ByteArrayInputStream(gz(file)) }

    @Test fun findsABarcode() = runTest {
        val food = bundled.byBarcode("8710400000001")!!
        assertEquals("Hagelslag puur", food.name)
        assertEquals("De Ruijter", food.brand)
        assertEquals(480.0, food.per100g.kcal, 0.0)
        assertNull(food.per100g.fibre)
        assertEquals(0.03, food.per100g.salt!!, 1e-9)
        assertEquals(15.0, food.servingG)
        assertEquals(400.0, food.packageG)
        assertEquals(FoodOrigin.OFF, food.source)
        // Same id as the same product looked up online, so both phones and the cache agree.
        assertEquals(offId("8710400000001"), food.id)
    }

    @Test fun drinksAreMarked() = runTest {
        assertTrue(bundled.byBarcode("5449000000996")!!.isDrink)
    }

    @Test fun findsAnEan13WithTheLeadingZeroLeftOut() = runTest {
        // OFF lists this one with 12 digits; the scanner reads 13.
        val food = bundled.byBarcode("0012345678905")!!
        assertEquals("Peanut butter", food.name)
        assertEquals("0012345678905", food.barcode)
        assertEquals(offId("0012345678905"), food.id)
    }

    @Test fun unknownBarcodeOrMissingFileGivesNull() = runTest {
        assertNull(bundled.byBarcode("8710400000002"))
        assertNull(BundledProducts { null }.byBarcode("8710400000001"))
    }

    @Test fun searchFindsNameAndBrandWords() = runTest {
        assertEquals(listOf("Hagelslag puur"), bundled.search("ruijter hagel").map { it.name })
        assertEquals(listOf("Cola"), bundled.search("coca").map { it.name })
        assertTrue(bundled.search("pizza").isEmpty())
    }

    @Test fun badLinesAreSkipped() {
        assertNull(BundledProducts.parse("123|too|few"))
        assertNull(BundledProducts.parse("8710400000001|No kcal|||1|1|1||||||0"))
    }

    @Test fun barcodeSourceUsesTheBundledListBeforeTheInternet() = runTest {
        val foods = FakeFoods()
        val web = FakeWeb { WebResponse(404, "") }
        val source = RealFoodSource(foods, OpenFoodFacts(web), {}, { emptyMap() }, bundled = bundled)
        assertEquals("Hagelslag puur", source.byBarcode("8710400000001")?.name)
        assertTrue(web.urls.isEmpty())
        assertEquals("Hagelslag puur", foods.byBarcode("8710400000001")?.name)
        // Not in the list: asks Open Food Facts online as before.
        assertNull(source.byBarcode("8710400000002"))
        assertEquals(1, web.urls.size)
    }

    @Test fun searchOnlineShowsBundledProductsWithoutInternet() = runTest {
        val web = FakeWeb().apply { offline = true }
        val source = RealFoodSource(FakeFoods(), OpenFoodFacts(web), {}, { emptyMap() }, bundled = bundled)
        assertEquals(listOf("Hagelslag puur"), source.searchOnline("hagelslag").map { it.name })
    }
}
