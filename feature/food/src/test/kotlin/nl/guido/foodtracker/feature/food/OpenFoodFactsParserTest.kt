package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.feature.food.data.FoodIds
import nl.guido.foodtracker.feature.food.off.OpenFoodFactsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsParserTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun `reads a product with Dutch name, first brand and drink flag`() {
        val food = OpenFoodFactsParser.parseProduct(resource("off-product-cola.json"), "5449000000996")!!
        assertEquals("Coca-Cola Original Taste", food.name)
        assertEquals("Coca-Cola", food.brand)
        assertEquals(42.0, food.per100g.kcal, 0.001)
        assertEquals(10.6, food.per100g.carbs, 0.001)
        assertEquals(FoodOrigin.OFF, food.source)
        assertEquals(FoodIds.openFoodFacts("5449000000996"), food.id)
        assertTrue(food.isDrink)
    }

    @Test fun `unknown product is null`() {
        assertNull(OpenFoodFactsParser.parseProduct("""{"code":"123","status":0,"status_verbose":"product not found"}""", "123"))
    }

    @Test fun `product without energy is null`() {
        val body = """{"status":1,"product":{"product_name":"Mystery","nutriments":{"fat_100g":3}}}"""
        assertNull(OpenFoodFactsParser.parseProduct(body, "1"))
    }

    @Test fun `search skips nameless products, reads text numbers and converts kJ`() {
        val foods = OpenFoodFactsParser.parseSearch(resource("off-search.json"))
        assertEquals(listOf("Halfvolle melk", "Volle melk"), foods.map { it.name })
        assertEquals(46.0, foods[0].per100g.kcal, 0.001)
        assertEquals(3.4, foods[0].per100g.protein, 0.001)
        assertEquals(64.0, foods[1].per100g.kcal, 0.1)
        assertFalse(foods[0].isDrink)
        assertNull(foods[1].brand)
    }
}
