package nl.guido.foodtracker.feature.food.off

/**
 * Allows at most [maxCalls] calls in any [windowMillis]. Open Food Facts asks apps to stay under
 * their limits; when we are over, we skip the call instead of waiting.
 */
internal class RateLimiter(
    private val maxCalls: Int,
    private val windowMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val calls = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock()
        while (calls.isNotEmpty() && now - calls.first() >= windowMillis) calls.removeFirst()
        if (calls.size >= maxCalls) return false
        calls.addLast(now)
        return true
    }
}
