package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.feature.food.dishes.DISH_ASSET
import nl.guido.foodtracker.feature.food.dishes.Dish
import nl.guido.foodtracker.feature.food.dishes.DishListParser
import nl.guido.foodtracker.feature.food.dishes.dishKey
import nl.guido.foodtracker.feature.food.dishes.findDish
import nl.guido.foodtracker.feature.food.dishes.matchDishes
import nl.guido.foodtracker.feature.food.eatout.mealAt
import nl.guido.foodtracker.feature.food.eatout.restaurantEntry
import nl.guido.foodtracker.feature.food.eatout.typedKcal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DishesTest {
    private val list = File("src/main/assets/$DISH_ASSET").bufferedReader().use { DishListParser.parse(it) }

    @Test fun bundledListIsSensible() {
        assertTrue(list.size >= 150)
        list.forEach { d ->
            assertTrue(d.name, d.estimate.low in 1..d.estimate.typical && d.estimate.typical <= d.estimate.high)
        }
        val keys = list.flatMap { d -> (listOf(d.name) + d.otherNames).map { dishKey(it) }.distinct() }
        assertEquals("every name points to one dish", keys.size, keys.toSet().size)
    }

    @Test fun findsPopularDishesByOtherNames() {
        assertEquals("Fries with mayonnaise", findDish("Patatje mayo", list)?.name)
        assertEquals("Döner kebab", findDish("broodje doner", list)?.name)
        assertNull(findDish("unicorn stew", list))
    }

    @Test fun typingShowsBestMatchesFirst() {
        assertEquals("Pizza margherita", matchDishes("margherita", list).first().name)
        val pizzas = matchDishes("pizza", list)
        assertTrue(pizzas.all { it.name.startsWith("Pizza") })
        assertEquals(listOf("Shoarma broodje"), matchDishes("broodje shoarma", list).map { it.name })
        assertTrue(matchDishes("  ", list).isEmpty())
    }

    @Test fun ownDishesComeFirst() {
        val mine = Dish("Pizza from Luigi", emptyList(), Estimate(900, 900, 900), own = true)
        assertEquals(mine, matchDishes("pizza", listOf(mine) + list).first())
        assertEquals(mine, findDish("pizza from luigi", listOf(mine) + list))
    }

    @Test fun mealFollowsTheClock() {
        assertEquals(Meal.BREAKFAST, mealAt(LocalTime.of(8, 0)))
        assertEquals(Meal.LUNCH, mealAt(LocalTime.of(12, 30)))
        assertEquals(Meal.DINNER, mealAt(LocalTime.of(19, 0)))
        assertEquals(Meal.SNACKS, mealAt(LocalTime.of(23, 0)))
    }

    @Test fun typedKcalMustBeSensible() {
        assertEquals(650, typedKcal(" 650 "))
        assertNull(typedKcal("0"))
        assertNull(typedKcal("abc"))
        assertNull(typedKcal("90000"))
    }

    @Test fun loggedAsEstimateWithoutWeight() {
        val entry = restaurantEntry("u1", LocalDate.of(2026, 10, 9), Meal.DINNER, " Pad thai ", Estimate(650, 800, 1000), Instant.EPOCH)
        assertTrue(entry.isEstimate)
        assertEquals(0.0, entry.portion.grams, 0.0)
        assertEquals(800.0, entry.nutrients.kcal, 0.0)
        val what = entry.what as? Logged.Restaurant ?: return fail("not a restaurant line")
        assertEquals("Pad thai", what.dish)
    }
}
