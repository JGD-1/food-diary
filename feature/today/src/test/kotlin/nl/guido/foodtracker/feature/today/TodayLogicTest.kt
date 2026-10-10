package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class TodayLogicTest {

    @Test
    fun `meal follows the time of day`() {
        assertEquals(Meal.BREAKFAST, mealForTime(LocalTime.of(7, 0)))
        assertEquals(Meal.LUNCH, mealForTime(LocalTime.of(12, 30)))
        assertEquals(Meal.DINNER, mealForTime(LocalTime.of(18, 0)))
        assertEquals(Meal.SNACKS, mealForTime(LocalTime.of(22, 0)))
    }

    @Test
    fun `kcal left is target minus eaten, including drinks and estimates`() {
        val today = listOf(
            line("Yoghurt", 194.0),
            restaurant("Club sandwich", 560),
            line("Cola", 139.0, Meal.DRINKS, grams = 330.0),
        )
        val s = todaySummary(today, emptyList(), targetKcal = 2100, currentMeal = Meal.DINNER)
        assertEquals(893, s.eatenKcal)
        assertEquals(1207, s.kcalLeft)
        assertTrue(s.hasEstimate)
        assertEquals(139, s.drinks.kcal)
        assertEquals(listOf(Meal.BREAKFAST, Meal.LUNCH), s.filledMeals.map { it.meal })
        assertEquals(893f / 2100f, s.ringFraction, 0.0001f)
    }

    @Test
    fun `going over target shows a negative number and a full ring`() {
        val s = todaySummary(listOf(line("Pasta", 2300.0)), emptyList(), targetKcal = 2100, currentMeal = Meal.DINNER)
        assertEquals(-200, s.kcalLeft)
        assertEquals(1f, s.ringFraction)
    }

    @Test
    fun `without a target there is nothing left to show`() {
        val s = todaySummary(listOf(line("Toast", 200.0)), emptyList(), targetKcal = null, currentMeal = Meal.LUNCH)
        assertNull(s.kcalLeft)
        assertEquals(0f, s.ringFraction)
    }

    @Test
    fun `the add card offers the first empty meal from now on`() {
        val today = listOf(line("Toast", 200.0, Meal.BREAKFAST), line("Soup", 300.0, Meal.LUNCH))
        assertEquals(Meal.DINNER, todaySummary(today, emptyList(), 2000, Meal.LUNCH).nextEmptyMeal)
        assertEquals(Meal.LUNCH, todaySummary(today.take(1), emptyList(), 2000, Meal.BREAKFAST).nextEmptyMeal)
        val all = today + line("Rice", 500.0, Meal.DINNER) + line("Nuts", 100.0, Meal.SNACKS)
        assertNull(todaySummary(all, emptyList(), 2000, Meal.SNACKS).nextEmptyMeal)
    }

    @Test
    fun `earlier empty meals are offered as small lines`() {
        val today = listOf(line("Soup", 300.0, Meal.LUNCH))
        val evening = todaySummary(today, emptyList(), 2000, Meal.SNACKS)
        assertEquals(listOf(Meal.BREAKFAST, Meal.DINNER), evening.earlierEmptyMeals)
        assertEquals(Meal.SNACKS, evening.nextEmptyMeal)
        assertEquals(emptyList<Meal>(), todaySummary(today, emptyList(), 2000, Meal.BREAKFAST).earlierEmptyMeals)
    }

    @Test
    fun `an earlier day offers every empty meal as a small line`() {
        val day = todaySummary(listOf(line("Soup", 300.0, Meal.LUNCH)), emptyList(), 2000, currentMeal = null)
        assertNull(day.nextEmptyMeal)
        assertEquals(listOf(Meal.BREAKFAST, Meal.DINNER, Meal.SNACKS), day.earlierEmptyMeals)
    }

    @Test
    fun `the arrows stop at today and routes carry only other days`() {
        assertEquals(TODAY, nextDay(TODAY.minusDays(1), TODAY))
        assertNull(nextDay(TODAY, TODAY))
        assertNull(routeDate(TODAY, TODAY))
        assertEquals(TODAY.minusDays(2), routeDate(TODAY.minusDays(2), TODAY))
        assertEquals(TODAY, parseDate("2026-10-12"))
        assertNull(parseDate("not a day"))
        assertNull(parseDate(null))
    }

    @Test
    fun `move keeps the lines and changes only the meal`() {
        val lunch = listOf(line("Soup", 300.0, Meal.LUNCH), line("Bread", 150.0, Meal.LUNCH))
        val moved = moveEntries(lunch, Meal.DINNER)
        assertEquals(lunch.map { it.id }, moved.map { it.id })
        assertTrue(moved.all { it.meal == Meal.DINNER })
        assertEquals(listOf(Meal.BREAKFAST, Meal.DINNER, Meal.SNACKS), moveTargets(Meal.LUNCH))
        assertTrue(moveTargets(Meal.DRINKS).isEmpty())
    }

    @Test
    fun `again on an empty meal repeats the most recent earlier day`() {
        val history = listOf(
            line("Pasta", 600.0, Meal.DINNER, TODAY.minusDays(3)),
            line("Salad", 200.0, Meal.DINNER, TODAY.minusDays(1)),
            line("Bread", 150.0, Meal.DINNER, TODAY.minusDays(1)),
        )
        val s = todaySummary(emptyList(), history, 2000, Meal.DINNER)
        assertEquals(listOf("Salad", "Bread"), againSource(s, Meal.DINNER).map { it.displayName })
    }

    @Test
    fun `again on a filled meal logs today's items once more`() {
        val history = listOf(line("Pasta", 600.0, Meal.SNACKS, TODAY.minusDays(1)))
        val s = todaySummary(listOf(line("Apple", 80.0, Meal.SNACKS)), history, 2000, Meal.SNACKS)
        assertEquals(listOf("Apple"), againSource(s, Meal.SNACKS).map { it.displayName })
    }

    @Test
    fun `copies get new ids, today's date and the chosen meal`() {
        val old = line("Salad", 200.0, Meal.DINNER, TODAY.minusDays(2))
        val now = Instant.parse("2026-10-12T18:00:00Z")
        val copy = copyEntries(listOf(old), TODAY, now, Meal.LUNCH) { "new" }.single()
        assertEquals("new", copy.id)
        assertEquals(TODAY, copy.date)
        assertEquals(Meal.LUNCH, copy.meal)
        assertEquals(now, copy.createdAt)
        assertEquals(old.nutrients, copy.nutrients)
    }

    @Test
    fun `changing an amount scales the nutrients`() {
        val yoghurt = line("Yoghurt", 194.0, grams = 200.0)
        val changed = withGrams(yoghurt, 150.0)
        assertEquals(150.0, changed.portion.grams, 0.0)
        assertEquals(145.5, changed.nutrients.kcal, 0.001)
        assertEquals(yoghurt.nutrients.protein * 0.75, changed.nutrients.protein, 0.001)
    }

    @Test
    fun `restaurant estimates have no amount to change`() {
        assertFalse(canChangeAmount(restaurant("Pizza", 800)))
        assertTrue(canChangeAmount(line("Yoghurt", 100.0)))
    }

    @Test
    fun `typed amounts accept commas and refuse nonsense`() {
        assertEquals(150.5, parseGrams(" 150,5 ")!!, 0.0)
        assertEquals(80.0, parseGrams("80")!!, 0.0)
        assertNull(parseGrams(""))
        assertNull(parseGrams("0"))
        assertNull(parseGrams("abc"))
        assertNull(parseGrams("99999"))
    }

    @Test
    fun `macro shares add up to about a hundred percent`() {
        val shares = macroShares(nl.guido.foodtracker.core.model.Nutrients(0.0, 60.0, 128.0, 38.0))!!
        assertTrue(kotlin.math.abs(shares.protein + shares.carbs + shares.fat - 100) <= 1)
        assertNull(macroShares(nl.guido.foodtracker.core.model.Nutrients.ZERO))
    }

    @Test
    fun `day totals add up per day`() {
        val totals = dayTotals(
            listOf(line("A", 100.0, date = TODAY.minusDays(1)), line("B", 50.0, date = TODAY.minusDays(1)), line("C", 70.0)),
        )
        assertEquals(listOf(150.0, 70.0), totals.map { it.kcal })
    }

    @Test
    fun `camera results go to Drinks for drinks, else to the meal for the time`() {
        val evening = java.time.LocalTime.of(19, 0)
        assertEquals(Meal.DINNER, mealForCameraResult(isDrink = false, fromDrinks = false, time = evening))
        assertEquals(Meal.DRINKS, mealForCameraResult(isDrink = true, fromDrinks = false, time = evening))
        assertEquals(Meal.DRINKS, mealForCameraResult(isDrink = false, fromDrinks = true, time = evening))
    }

    @Test
    fun `carbs per meal add up the meal's lines`() {
        assertEquals(50.0, mealCarbs(listOf(line("Soup", 300.0), line("Bread", 200.0))), 0.001)
    }

    @Test
    fun `fibre, sugar and salt show only when known`() {
        assertTrue(extraNutrients(Nutrients(500.0, 20.0, 60.0, 10.0)).isEmpty())
        val some = Nutrients(500.0, 20.0, 60.0, 10.0, fibre = 4.0) + Nutrients(100.0, 1.0, 1.0, 1.0, salt = 0.6)
        assertEquals(listOf(ExtraNutrient.FIBRE to 4.0, ExtraNutrient.SALT to 0.6), extraNutrients(some))
    }
}
