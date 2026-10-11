package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import nl.guido.foodtracker.feature.food.off.OpenFoodFacts
import nl.guido.foodtracker.feature.food.search.EVERYDAY_ASSET
import nl.guido.foodtracker.feature.food.search.EverydayWords
import nl.guido.foodtracker.feature.food.search.rankFoods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** S2: everyday words find the NEVO food they mean ("havermout" → oat flakes). */
class EverydayWordsTest {
    private val words = File("src/main/assets/$EVERYDAY_ASSET").bufferedReader().use { EverydayWords.parse(it) }
    private val file = File("src/main/assets/$NEVO_ASSET")
    private val foods = file.bufferedReader().use { NevoParser.parse(it) }
    private val info = file.bufferedReader().use { NevoParser.info(it) }

    @Test fun everyCodeIsABundledNevoFood() {
        val ids = foods.map { it.id }.toSet()
        val lines = File("src/main/assets/$EVERYDAY_ASSET").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size >= 30)
        lines.flatMap { it.split('|')[1].split(';') }.forEach { code -> assertTrue("NEVO $code", nevoId(code.trim()) in ids) }
    }

    @Test fun wholeTypedTextOnly() {
        assertEquals(nevoId("213"), words.foodsFor("Havermout ").first())
        assertEquals(nevoId("213"), words.foodsFor("oats").first())
        assertEquals(nevoId("84"), words.foodsFor("eieren").first())
        assertEquals(nevoId("1311"), words.foodsFor("hagelslag").first())
        assertEquals(emptyList<String>(), words.foodsFor("kip rauw"))
        assertEquals(emptyList<String>(), words.foodsFor("haverm"))
    }

    private fun top(text: String, n: Int = 1) =
        rankFoods(text, foods, nevo = info, everyday = words.foodsFor(text)).take(n).map { it.name }

    @Test fun everydayFoodComesFirst() {
        assertEquals(listOf("Oat flakes"), top("havermout"))
        assertEquals(listOf("Oat flakes"), top("oatmeal"))
        assertEquals(listOf("Wheat bread wholemeal av fine and coarse"), top("boterham"))
        assertEquals(listOf("Coffee prepared"), top("coffee"))
        assertEquals(listOf("Yoghurt half fat"), top("yogurt"))
        assertEquals(listOf("Chips fried in liquid frying fat"), top("patat"))
    }

    @Test fun foodSourceAddsEverydayFoods() = runTest {
        val source = RealFoodSource(FakeFoods(*foods.toTypedArray()), OpenFoodFacts(FakeWeb()), {}, { info }, everydayWords = { words })
        // "Vlokken haver-" / "Oat flakes" holds neither word, yet comes first.
        assertEquals("Oat flakes", source.search("havermout").first().name)
        assertEquals("Wheat bread wholemeal av fine and coarse", source.search("boterham").first().name)
    }
}
