package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import nl.guido.foodtracker.feature.food.search.rankFoods
import nl.guido.foodtracker.feature.food.usda.USDA_ASSET
import nl.guido.foodtracker.feature.food.usda.UsdaParser
import nl.guido.foodtracker.feature.food.usda.usdaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsdaTest {
    private val usda = File("src/main/assets/$USDA_ASSET")
    private val nevo = File("src/main/assets/$NEVO_ASSET")

    @Test fun bundledVegetablesReadWithCarbsWithoutFibre() {
        val all = usda.bufferedReader().use { UsdaParser.parse(it, dutch = true) }
        assertTrue(all.size >= 7)
        assertTrue(all.all { it.source == FoodOrigin.USDA && it.ownerId == null })
        val shiitake = all.first { it.id == usdaId("169242") }
        assertEquals("Shiitake rauw", shiitake.name)
        assertEquals(34.0, shiitake.per100g.kcal, 0.0)
        assertEquals(6.79 - 2.5, shiitake.per100g.carbs, 1e-9)
        assertEquals(2.5, shiitake.per100g.fibre!!, 0.0)
        assertEquals(9 * 2.5 / 1000, shiitake.per100g.salt!!, 1e-9)
        // Roughly the energy of its parts (4 kcal/g protein and carbs, 9 fat, 2 fibre): no typing slips.
        all.forEach { f ->
            val n = f.per100g
            val parts = 4 * n.protein + 4 * n.carbs + 9 * n.fat + 2 * (n.fibre ?: 0.0)
            assertTrue("${f.name}: ${n.kcal} vs $parts", kotlin.math.abs(n.kcal - parts) <= maxOf(10.0, n.kcal * 0.3))
        }
    }

    @Test fun neitherDoublesNevoNorClashesWithIt() {
        val nevoNames = nevo.bufferedReader().use { NevoParser.parse(it, dutch = true) }.map { it.name.lowercase() }.toSet()
        val usdaFoods = usda.bufferedReader().use { UsdaParser.parse(it, dutch = true) }
        assertTrue(usdaFoods.none { it.name.lowercase() in nevoNames })
        assertEquals(usdaFoods.size, usdaFoods.map { it.id }.toSet().size)
    }

    @Test fun searchFindsThemByDutchAndEnglishName() {
        val foods = nevo.bufferedReader().use { NevoParser.parse(it, dutch = true) } + usda.bufferedReader().use { UsdaParser.parse(it, dutch = true) }
        val info = nevo.bufferedReader().use { NevoParser.info(it) } + usda.bufferedReader().use { UsdaParser.info(it) }
        fun top(text: String) = rankFoods(text, foods, nevo = info).first().name
        assertEquals("Shiitake rauw", top("shiitake"))
        assertEquals("Oesterzwam rauw", top("oesterzwam"))
        assertEquals("Sjalot rauw", top("sjalot"))
        assertEquals("Edamame diepvries bereid", top("edamame"))
        assertEquals("Waterkers rauw", top("watercress"))
    }
}
