package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NevoTest {
    private val all = File("src/main/assets/$NEVO_ASSET").bufferedReader().use { NevoParser.parse(it) }

    @Test fun bundledFileHasEveryFood() {
        assertEquals(2275, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.source == FoodOrigin.NEVO && it.ownerId == null && it.name.isNotBlank() })
    }

    @Test fun valuesAreReadUnchanged() {
        val potatoes = all.first { it.id == nevoId("1") }
        assertEquals("Potatoes raw", potatoes.name)
        assertEquals(88.0, potatoes.per100g.kcal, 0.0)
        assertEquals(2.0, potatoes.per100g.protein, 0.0)
        assertEquals(19.0, potatoes.per100g.carbs, 0.0)
        assertFalse(potatoes.isDrink)
    }

    @Test fun decimalCommaAndDrinks() {
        val parsed = NevoParser.parse(
            "code|group|name_nl|name_en|per|kcal|protein|carbs|fat\n7|Alcoholic beverages|Bier|Beer|per 100g|41|0,5|3,1|0\n".reader(),
        ).single()
        assertEquals(0.5, parsed.per100g.protein, 0.0)
        assertEquals(3.1, parsed.per100g.carbs, 0.0)
        assertTrue(parsed.isDrink)
        assertEquals(nevoId("7"), parsed.id)
    }

    @Test fun per100mlRowsAreSkippedAndDutchNamesKept() {
        val text = "1|Potatoes and tubers|Aardappelen rauw|Potatoes raw|per 100g|88|2|19|0\n" +
            "9|Foods for special nutritional use|Zuigelingenvoeding|Infant formula|per 100ml|67|1,3|7|3,5\n"
        assertEquals(listOf("Potatoes raw"), NevoParser.parse(text.reader()).map { it.name })
        assertEquals(mapOf(nevoId("1") to "Aardappelen rauw"), NevoParser.dutchNames(text.reader()))
    }

    @Test fun fibreSugarAndSaltFromTheBundledFile() {
        // Potatoes raw: FIBT 1,8 g, SUGAR 1 g, NA 2 mg in NEVO2025_v9.0.csv.
        val potatoes = all.first { it.id == nevoId("1") }.per100g
        assertEquals(1.8, potatoes.fibre!!, 1e-9)
        assertEquals(1.0, potatoes.sugar!!, 1e-9)
        assertEquals(0.005, potatoes.salt!!, 1e-9)
        assertTrue(all.count { it.per100g.fibre != null } > 2200)
    }

    @Test fun emptyValuesAreUnknownAndOldLinesStillRead() {
        val text = "3447|Nuts and seeds|Chiazaad gedroogd|Chia seeds dried|per 100g|443|16,5|7,7|30,7|34,4||16\n" +
            "1|Potatoes and tubers|Aardappelen rauw|Potatoes raw|per 100g|88|2|19|0\n"
        val (chia, potatoes) = NevoParser.parse(text.reader())
        assertEquals(34.4, chia.per100g.fibre!!, 1e-9)
        assertNull(chia.per100g.sugar)
        assertEquals(0.04, chia.per100g.salt!!, 1e-9)
        assertNull(potatoes.per100g.fibre)
        assertEquals(88.0, potatoes.per100g.kcal, 0.0)
    }

    @Test fun idsAreNameBased() {
        assertEquals(java.util.UUID.nameUUIDFromBytes("nevo:1".toByteArray()).toString(), nevoId("1"))
    }
}
