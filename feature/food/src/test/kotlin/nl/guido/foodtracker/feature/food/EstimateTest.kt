package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.feature.food.eatout.mealAt
import nl.guido.foodtracker.feature.food.eatout.restaurantEntry
import nl.guido.foodtracker.feature.food.eatout.typedKcal
import nl.guido.foodtracker.feature.food.estimate.EstimateCache
import nl.guido.foodtracker.feature.food.estimate.EstimateFailure
import nl.guido.foodtracker.feature.food.estimate.EstimateServer
import nl.guido.foodtracker.feature.food.estimate.SupabaseRestaurantEstimator
import nl.guido.foodtracker.feature.food.estimate.dishKey
import nl.guido.foodtracker.feature.food.net.WebResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private class MemoryCache : EstimateCache {
    val map = mutableMapOf<String, Estimate>()
    override fun get(key: String) = map[key]
    override fun put(key: String, estimate: Estimate) { map[key] = estimate }
}

class EstimateTest {
    private val server = EstimateServer("https://abc.supabase.co", "anon")

    @Test fun dishKeyIgnoresCaseAndPunctuation() {
        assertEquals("pizza margherita", dishKey("  Pizza, Margherita!! "))
        assertEquals("crème brûlée", dishKey("Crème  Brûlée"))
    }

    @Test fun asksTheFunctionOnceThenUsesTheCache() = runTest {
        val web = FakeWeb { WebResponse(200, """{"low":450,"typical":560,"high":680,"cached":false}""") }
        val estimator = SupabaseRestaurantEstimator(server, web, MemoryCache())
        assertEquals(Estimate(450, 560, 680), estimator.estimate("Pizza margherita"))
        assertEquals(Estimate(450, 560, 680), estimator.estimate("pizza  MARGHERITA"))
        assertEquals(listOf("https://abc.supabase.co/functions/v1/estimate"), web.urls)
        assertEquals("Bearer anon", web.headers.single()["Authorization"])
        assertEquals("""{"dish":"Pizza margherita"}""", web.bodies.single())
    }

    @Test fun failuresSayWhy() = runTest {
        suspend fun reasonFor(web: FakeWeb, s: EstimateServer = server): EstimateFailure.Reason? = try {
            SupabaseRestaurantEstimator(s, web, MemoryCache()).estimate("soup"); null
        } catch (e: EstimateFailure) {
            e.reason
        }
        assertEquals(EstimateFailure.Reason.OFFLINE, reasonFor(FakeWeb().apply { offline = true }))
        assertEquals(EstimateFailure.Reason.NOT_SET_UP, reasonFor(FakeWeb { WebResponse(503, "") }))
        assertEquals(EstimateFailure.Reason.NOT_SET_UP, reasonFor(FakeWeb(), EstimateServer("", "")))
        assertEquals(EstimateFailure.Reason.FAILED, reasonFor(FakeWeb { WebResponse(500, "") }))
        assertEquals(EstimateFailure.Reason.FAILED, reasonFor(FakeWeb { WebResponse(200, """{"low":900,"typical":500,"high":600}""") }))
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
        val entry = restaurantEntry("u1", LocalDate.of(2026, 10, 9), Meal.DINNER, " Pad thai ", Estimate(500, 650, 800), Instant.EPOCH)
        assertTrue(entry.isEstimate)
        assertEquals(0.0, entry.portion.grams, 0.0)
        assertEquals(650.0, entry.nutrients.kcal, 0.0)
        val what = entry.what as? Logged.Restaurant ?: return fail("not a restaurant line")
        assertEquals("Pad thai", what.dish)
        assertEquals(Estimate(500, 650, 800), what.estimate)
    }
}
