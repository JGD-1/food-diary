package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.search.Candidate
import nl.guido.foodtracker.feature.food.search.FoodRanking
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodRankingTest {
    private fun food(id: String, name: String, source: FoodOrigin, brand: String? = null) =
        Food(id, name, brand = brand, per100g = Nutrients(1.0, 0.0, 0.0, 0.0), source = source)

    @Test fun `exact and prefix names first, own foods before NEVO before products`() {
        val candidates = listOf(
            Candidate(food("a", "Halfvolle melk", FoodOrigin.OFF, brand = "AH")),
            Candidate(food("b", "Milk semi-skimmed", FoodOrigin.NEVO), listOf("Melk halfvolle")),
            Candidate(food("c", "Melk", FoodOrigin.MANUAL)),
            Candidate(food("d", "Chocolate milk", FoodOrigin.NEVO), listOf("Chocolademelk")),
        )
        assertEquals(listOf("c", "b", "a", "d"), FoodRanking.rank("melk", candidates).map { it.id })
    }

    @Test fun `every word must match, in any order, ignoring accents and case`() {
        val candidates = listOf(
            Candidate(food("a", "Crème fraîche", FoodOrigin.NEVO)),
            Candidate(food("b", "Bread wholemeal", FoodOrigin.NEVO), listOf("Brood volkoren")),
            Candidate(food("c", "Bread white", FoodOrigin.NEVO), listOf("Brood wit")),
        )
        assertEquals(listOf("a"), FoodRanking.rank("CREME", candidates).map { it.id })
        assertEquals(listOf("b"), FoodRanking.rank("volkoren brood", candidates).map { it.id })
        assertEquals(emptyList<Food>(), FoodRanking.rank("   ", candidates))
    }

    @Test fun `probe is the longest word`() {
        assertEquals("volkoren", FoodRanking.probe("brood volkoren"))
    }

}
