package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeighAfterTest {

    private val crisps = WeighAfterItem(
        id = "w1", userId = "me", foodId = "crisps", title = "Crisps, paprika", startGrams = 150.0,
        meal = Meal.SNACKS, date = TODAY, startedAt = Instant.ofEpochMilli(1_760_000_000_000),
    )

    @Test fun `what was eaten is the start minus what is left`() {
        assertEquals(110.0, eatenGrams(150.0, 40.0)!!, 0.001)
        assertEquals(150.0, eatenGrams(150.0, 0.0)!!, 0.001)
    }

    @Test fun `nothing eaten, or more left than at the start, logs nothing`() {
        assertNull(eatenGrams(150.0, 150.0))
        assertNull(eatenGrams(150.0, 180.0))
        assertNull(eatenGrams(150.0, -5.0))
    }

    @Test fun `grams left may be zero`() {
        assertEquals(0.0, parseLeftGrams("0")!!, 0.0)
        assertEquals(12.5, parseLeftGrams(" 12,5 ")!!, 0.0)
        assertNull(parseLeftGrams("-3"))
        assertNull(parseLeftGrams("lots"))
    }

    @Test fun `items are stored and read back`() {
        val cheese = crisps.copy(id = "w2", foodId = "parmesan", title = "Parmesan\twith\na tab", startGrams = 212.5, meal = Meal.DINNER)
        val back = decodeWeighAfter(encodeWeighAfter(listOf(crisps, cheese)))
        assertEquals(listOf(crisps, cheese.copy(title = "Parmesan with a tab")), back)
    }

    @Test fun `unreadable lines are skipped`() {
        val text = encodeWeighAfter(listOf(crisps)) + "\nnot\tan\titem\n"
        assertEquals(listOf(crisps), decodeWeighAfter(text))
        assertTrue(decodeWeighAfter(null).isEmpty())
        assertFalse(decodeWeighAfter(encodeWeighAfter(emptyList())).isNotEmpty())
    }
}
