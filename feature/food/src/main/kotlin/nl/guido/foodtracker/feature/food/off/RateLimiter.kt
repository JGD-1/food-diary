package nl.guido.foodtracker.feature.food.off

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Allows at most [maxCalls] calls in any [windowMs] window. Open Food Facts asks for
 * 15 product reads and 10 searches a minute; a person scanning never gets near that,
 * but a fast run of scans just waits a moment instead of being blocked by them.
 */
internal class RateLimiter(
    private val maxCalls: Int,
    private val windowMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val calls = ArrayDeque<Long>()

    /** How long to wait (ms) before the next call is allowed, and books that call. */
    suspend fun reserve(): Long = mutex.withLock {
        val now = clock()
        while (calls.isNotEmpty() && calls.first() <= now - windowMs) calls.removeFirst()
        val at = if (calls.size < maxCalls) now else calls[calls.size - maxCalls] + windowMs
        calls.addLast(at)
        at - now
    }

    suspend fun <T> run(block: suspend () -> T): T {
        val wait = reserve()
        if (wait > 0) delay(wait)
        return block()
    }
}
