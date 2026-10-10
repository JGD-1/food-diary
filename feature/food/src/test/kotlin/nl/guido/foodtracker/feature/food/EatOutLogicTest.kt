package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.feature.food.eatout.PortionSize
import nl.guido.foodtracker.feature.food.eatout.dayFrom
import nl.guido.foodtracker.feature.food.eatout.dishWithSize
import nl.guido.foodtracker.feature.food.eatout.scaled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class EatOutLogicTest {
    private val pizza = Estimate(low = 701, typical = 850, high = 1001)

    @Test
    fun halfAndOneAndAHalfScaleTheWholeRange() {
        assertEquals(Estimate(351, 425, 501), pizza.scaled(PortionSize.HALF))
        assertEquals(Estimate(1052, 1275, 1502), pizza.scaled(PortionSize.ONE_AND_HALF))
    }

    @Test
    fun wholeKeepsTheDishAsItIs() {
        assertSame(pizza, pizza.scaled(PortionSize.WHOLE))
    }

    @Test
    fun theDiaryLineSaysHowMuch() {
        assertEquals("½ Pizza margherita", dishWithSize(" Pizza margherita ", PortionSize.HALF))
        assertEquals("1½ Pizza margherita", dishWithSize("Pizza margherita", PortionSize.ONE_AND_HALF))
        assertEquals("Pizza margherita", dishWithSize("Pizza margherita", PortionSize.WHOLE))
    }

    @Test
    fun logsToTheDayTodayWasShowing() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 8), dayFrom("2026-10-08", today))
        assertEquals(today, dayFrom(null, today))
        assertEquals(today, dayFrom("not a day", today))
    }
}
