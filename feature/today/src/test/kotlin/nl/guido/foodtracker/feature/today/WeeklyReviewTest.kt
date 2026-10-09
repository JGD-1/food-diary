package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.WeighIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyReviewTest {

    private fun days(count: Int, kcal: Double) = (1..count).map { line("Food", kcal, date = TODAY.minusDays(it.toLong())) }
    private fun weighIn(daysAgo: Long, kg: Double) = WeighIn("w$daysAgo", "me", TODAY.minusDays(daysAgo), kg)

    @Test
    fun `only shown on Monday`() {
        assertNull(weeklyReview(TODAY.plusDays(1), days(7, 2000.0), emptyList(), 2000))
    }

    @Test
    fun `nothing to look back on means no card`() {
        assertNull(weeklyReview(TODAY, emptyList(), emptyList(), 2000))
    }

    @Test
    fun `average over logged days, weigh-in change and a calm suggestion`() {
        val entries = days(5, 2000.0) + line("Old", 9999.0, date = TODAY.minusDays(9)) + line("Today", 500.0)
        val review = weeklyReview(TODAY, entries, listOf(weighIn(10, 82.5), weighIn(3, 82.1)), 2100)!!
        assertEquals(5, review.daysLogged)
        assertEquals(2000, review.averageKcal)
        assertEquals(82.1, review.latestWeighIn!!.kg, 0.0)
        assertEquals(-0.4, review.weighInChangeKg!!, 0.0001)
        assertEquals(Suggestion.KEEP_GOING, review.suggestion)
    }

    @Test
    fun `suggestions in order of what helps most`() {
        val weighed = listOf(weighIn(2, 80.0))
        assertEquals(Suggestion.SET_TARGET, weeklyReview(TODAY, days(7, 2000.0), weighed, null)!!.suggestion)
        assertEquals(Suggestion.LOG_MORE_DAYS, weeklyReview(TODAY, days(3, 2000.0), weighed, 2000)!!.suggestion)
        assertEquals(Suggestion.WEIGH_IN, weeklyReview(TODAY, days(6, 2000.0), emptyList(), 2000)!!.suggestion)
        assertEquals(Suggestion.ABOVE_TARGET, weeklyReview(TODAY, days(6, 2300.0), weighed, 2000)!!.suggestion)
        assertEquals(Suggestion.WELL_BELOW_TARGET, weeklyReview(TODAY, days(6, 1500.0), weighed, 2000)!!.suggestion)
    }

    @Test
    fun `a weigh-in this Monday morning counts`() {
        val review = weeklyReview(TODAY, days(5, 2000.0), listOf(weighIn(0, 79.9)), 2000)!!
        assertEquals(79.9, review.latestWeighIn!!.kg, 0.0)
        assertNull(review.weighInChangeKg)
    }
}
