package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import nl.guido.foodtracker.feature.food.pieces.BundledCommonPortions
import nl.guido.foodtracker.feature.food.pieces.CommonPiecesParser
import nl.guido.foodtracker.feature.food.pieces.PIECES_ASSET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommonPiecesTest {
    private val pieces = File("src/main/assets/$PIECES_ASSET").bufferedReader().use { CommonPiecesParser.parse(it) }
    private val nevo = File("src/main/assets/$NEVO_ASSET").bufferedReader().use { NevoParser.parse(it) }.associateBy { it.id }

    @Test fun everyPieceBelongsToABundledNevoFood() {
        assertTrue(pieces.size > 50)
        assertTrue(pieces.keys.all { it in nevo })
        assertTrue(pieces.values.all { it.grams in 1.0..500.0 && it.english.isNotBlank() && it.dutch.isNotBlank() })
    }

    @Test fun oneAppleInThePhonesLanguage() = runTest {
        val apple = nevo.getValue(nevoId("875"))
        assertEquals(listOf(Portion(150.0, "1 apple")), BundledCommonPortions({ pieces }, { "en" }).forFood(apple))
        assertEquals(listOf(Portion(150.0, "1 appel")), BundledCommonPortions({ pieces }, { "nl" }).forFood(apple))
    }

    @Test fun otherFoodsHaveNoPieces() = runTest {
        val common = BundledCommonPortions({ pieces }, { "en" })
        assertEquals(emptyList<Portion>(), common.forFood(nevo.getValue(nevoId("4")))) // pasta
        val scanned = Food(nevoId("875"), "Apple", per100g = Nutrients(50.0, 0.0, 12.0, 0.0), source = FoodOrigin.LABEL)
        assertEquals(emptyList<Portion>(), common.forFood(scanned))
    }

    @Test fun aCodeIsListedOnce() {
        val lines = File("src/main/assets/$PIECES_ASSET").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val codes = lines.flatMap { it.split('|')[0].split(';') }
        assertEquals(codes.size, codes.toSet().size)
    }
}
