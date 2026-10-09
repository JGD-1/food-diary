package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.data.FoodIds
import nl.guido.foodtracker.feature.food.nevo.NevoNameIndex
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NevoTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun `reads the RIVM pipe format, keeping numbers unchanged`() {
        val rows = NevoParser.parse(resource("nevo-sample.csv"))
        assertEquals(listOf("1", "2", "3"), rows.map { it.code }) // endive: no kcal value; formula: per 100 ml
        val bread = rows[0]
        assertEquals("Brood volkoren", bread.nameNl)
        assertEquals("Bread wholemeal", bread.nameEn)
        assertEquals(236.0, bread.per100g.kcal, 0.0)
        assertEquals(9.7, bread.per100g.protein, 0.0) // PROT, not PROTPL
        assertEquals(39.4, bread.per100g.carbs, 0.0)
        assertEquals(3.1, bread.per100g.fat, 0.0) // FAT, not FACID
        assertFalse(bread.isDrink)
        assertTrue(rows[1].isDrink)
        assertEquals("Kaas Goudse 48+ \"jong\"", rows[2].nameNl)
    }

    @Test fun `reads semicolons and decimal commas`() {
        val rows = NevoParser.parse(resource("nevo-sample-semicolon.csv"))
        assertEquals(9.7, rows[0].per100g.protein, 0.0)
        assertTrue(rows[1].isDrink)
        val beer = rows[1].toFood()
        assertEquals("Beer lager", beer.name)
        assertEquals(FoodOrigin.NEVO, beer.source)
        assertEquals(FoodIds.nevo("5"), beer.id)
    }

    @Test(expected = NevoParser.FormatException::class)
    fun `a file without the energy column is refused`() {
        NevoParser.parse("NEVO-code|Voedingsmiddelnaam|PROT (g)|CHO (g)|FAT (g)\n1|Brood|1|2|3")
    }

    @Test fun `Dutch name index finds every word and survives saving`() {
        val index = NevoNameIndex.of(NevoParser.parse(resource("nevo-sample.csv")))
        assertEquals(listOf("1"), index.matches("volkoren brood"))
        assertEquals(listOf("3"), index.matches("goudse kaas"))
        val reloaded = NevoNameIndex.parse(index.serialize())
        assertEquals(index.names, reloaded.names)
    }

    @Test fun `reads the real NEVO 2025 file shipped in the app`() {
        val file = java.io.File("src/main/assets/nevo/NEVO2025_v9.0.csv")
        org.junit.Assume.assumeTrue(file.exists())
        val rows = NevoParser.parse(file.readText())
        assertEquals(2275, rows.size) // 2,328 foods minus 53 infant formulas measured per 100 ml
        val potato = rows.first { it.code == "1" }
        assertEquals("Aardappelen rauw", potato.nameNl)
        assertEquals("Potatoes raw", potato.nameEn)
        assertEquals(88.0, potato.per100g.kcal, 0.0)
        assertEquals(19.0, potato.per100g.carbs, 0.0)
        assertTrue(rows.count { it.isDrink } > 100)
    }

    @Test fun `ids are stable and differ per table`() {
        assertEquals(FoodIds.nevo("1"), FoodIds.nevo(" 1 "))
        assertTrue(FoodIds.nevo("1") != FoodIds.openFoodFacts("1"))
        assertEquals(36, FoodIds.nevo("1").length)
    }
}
