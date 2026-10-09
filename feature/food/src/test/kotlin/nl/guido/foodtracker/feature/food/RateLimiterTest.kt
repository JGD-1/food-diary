package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.feature.food.off.RateLimiter
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {
    @Test fun `allows the limit at once, then waits until the oldest call leaves the window`() = runTest {
        var now = 0L
        val limiter = RateLimiter(maxCalls = 3, windowMs = 60_000, clock = { now })
        assertEquals(listOf(0L, 0L, 0L), List(3) { limiter.reserve() })
        now = 10_000
        assertEquals(50_000L, limiter.reserve()) // waits for the call at 0 to be a minute old
        now = 61_000
        assertEquals(0L, limiter.reserve())
    }

    @Test fun `queued calls are spread over the window`() = runTest {
        val limiter = RateLimiter(maxCalls = 1, windowMs = 1_000, clock = { 0L })
        assertEquals(listOf(0L, 1_000L, 2_000L), List(3) { limiter.reserve() })
    }
}
