package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import nl.guido.foodtracker.feature.food.search.EverydayWords
import nl.guido.foodtracker.feature.food.search.rankFoods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Search order on the real bundled NEVO table: the plain, everyday food comes first. */
class SearchRankingTest {
    private val file = File("src/main/assets/$NEVO_ASSET")
    private val foods = file.bufferedReader().use { NevoParser.parse(it) }
    private val info = file.bufferedReader().use { NevoParser.info(it) }

    private fun top(text: String, n: Int = 3) = rankFoods(text, foods, nevo = info).take(n).map { it.name }

    @Test fun applesBeforeApplePie() {
        assertEquals(listOf("Apple w skin av", "Apple wo skin av"), top("apple", 2))
        assertEquals(listOf("Apple w skin av", "Apple wo skin av"), top("appel", 2))
        assertTrue(top("apple", 6).none { "pie" in it.lowercase() || "strudel" in it.lowercase() })
    }

    @Test fun everydayFoodsFirst() {
        assertTrue(top("bread").all { "bread" in it.lowercase() && "crumbs" !in it.lowercase() })
        assertTrue(top("brood").all { "bread" in it.lowercase() && "gluten" !in it.lowercase() })
        assertTrue(top("egg", 2).all { it.startsWith("Egg whole") })
        assertTrue(top("kip").all { it.startsWith("Chicken") })
        assertEquals("Banana", top("banana", 1).single())
        assertTrue(top("melk", 5).containsAll(listOf("Milk whole", "Milk skimmed", "Milk semi-skimmed")))
        assertTrue(top("potato").all { it.startsWith("Potato") })
    }

    @Test fun shortWordsOnlyMatchWordStarts() {
        // "ei" (egg) also sits inside words like "postelein" (purslane); only word starts count.
        assertTrue(top("ei", 5).all { it.startsWith("Egg whole") })
        assertTrue(rankFoods("ei", foods, nevo = info).none { it.name.startsWith("Purslane") })
    }

    @Test fun dutchNamesFindEnglishNamedFoods() {
        // Buttermilk is "Melk karne-" in NEVO, so "melk" finds it.
        assertTrue(rankFoods("melk", foods, nevo = info).any { it.name == "Buttermilk" })
    }

    private val dutch = file.bufferedReader().use { NevoParser.parse(it, dutch = true) }
    private val everyday = File("src/main/assets/everyday_words.csv").bufferedReader().use { EverydayWords.parse(it) }

    private fun topNl(text: String, n: Int = 2) =
        rankFoods(text, dutch, nevo = info, everyday = everyday.foodsFor(text)).take(n).map { it.name }

    @Test fun dutchCompoundsFindBackToFrontNevoNames() {
        // NEVO writes "bloemkool" as "Kool bloem-", "bleekselderij" as "Selderij bleek-".
        assertEquals(listOf("Kool bloem- rauw", "Kool bloem- gekookt"), topNl("bloemkool"))
        assertEquals("Selderij bleek- rauw", topNl("bleekselderij", 1).single())
        assertEquals("Sla ijsberg- rauw", topNl("ijsbergsla", 1).single())
        assertEquals("Kool spits- rauw", topNl("spitskool", 1).single())
        assertEquals("Kool boeren- rauw", topNl("boerenkool", 1).single())
        assertTrue(topNl("sperziebonen").all { it.startsWith("Bonen sperzie-") })
        assertEquals("Tomaat kers- rauw", topNl("kerstomaat", 1).single())
    }

    @Test fun accentsAndLittleForms() {
        assertEquals("Tauge rauw", topNl("taugé", 1).single())
        assertTrue(topNl("maïs", 1).single().startsWith("Mais"))
        assertTrue(topNl("bietjes").all { it.startsWith("Biet rode") })
        assertEquals("Radijs rauw", topNl("radijsjes", 1).single())
    }

    @Test fun everydayVegetableNames() {
        assertEquals("Ui sla- rauw", topNl("lenteui", 1).single())
        assertEquals("Ui sla- rauw", topNl("bosui", 1).single())
        assertEquals("Paprika rode rauw", topNl("puntpaprika", 1).single())
        assertEquals("Spinazie rauw", topNl("babyspinazie", 1).single())
        assertEquals("Tomaat kers- rauw", topNl("cherrytomaatjes", 1).single())
        assertEquals("Spinazie rauw", topNl("spinazie", 1).single())
        assertTrue(topNl("paprika", 1).single().startsWith("Paprika"))
        assertTrue(topNl("paprika's", 1).single().startsWith("Paprika"))
    }
}
