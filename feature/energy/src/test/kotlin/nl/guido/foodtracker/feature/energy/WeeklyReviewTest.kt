package nl.guido.foodtracker.feature.energy

import nl.guido.foodtracker.core.model.DayTotal
import nl.guido.foodtracker.core.model.ReviewSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {
    private val lastWeek = MONDAY.minusWeeks(1)

    private fun review(weighIns: List<nl.guido.foodtracker.core.model.WeighIn>, intake: List<DayTotal>, target: Int = 2000) =
        WeeklyReviews.review(MONDAY, PROFILE, weighIns, intake, target)

    @Test
    fun onlyMondayIsReviewDay() {
        assertTrue(WeeklyReviews.isReviewDay(MONDAY))
        assertFalse(WeeklyReviews.isReviewDay(MONDAY.plusDays(1)))
    }

    @Test
    fun averagesLoggedDaysOfLastWeekOnly() {
        val intake = dailyIntake(lastWeek, MONDAY, 1900.0) + DayTotal(MONDAY, 400.0) + DayTotal(lastWeek.minusDays(1), 5000.0)
        val result = review(weeklyWeighIns(85.0, 84.6), intake)
        assertEquals(lastWeek, result.weekStart)
        assertEquals(1900, result.averageKcal)
        assertEquals(7, result.daysLogged)
    }

    @Test
    fun mondayMorningWeighInCountsWithChange() {
        val result = review(weeklyWeighIns(85.0, 84.6), dailyIntake(lastWeek, MONDAY, 1900.0))
        assertEquals(84.6, result.weighInKg!!, 0.0)
        assertEquals(-0.4, result.changeKg!!, 1e-9)
        assertEquals(ReviewSuggestion.KEEP_GOING, result.suggestion)
    }

    @Test
    fun fewLoggedDaysSuggestsLoggingMore() {
        val result = review(weeklyWeighIns(85.0, 84.6), dailyIntake(lastWeek, lastWeek.plusDays(2), 1900.0))
        assertEquals(ReviewSuggestion.LOG_MORE_DAYS, result.suggestion)
    }

    @Test
    fun noRecentWeighInSuggestsWeighingIn() {
        val old = listOf(weighIn(MONDAY.minusWeeks(3), 85.0))
        val result = review(old, dailyIntake(lastWeek, MONDAY, 1900.0))
        assertNull(result.weighInKg)
        assertNull(result.changeKg)
        assertEquals(ReviewSuggestion.WEIGH_IN, result.suggestion)
    }

    @Test
    fun aboveAndBelowTarget() {
        val weighIns = weeklyWeighIns(85.0, 84.6)
        assertEquals(ReviewSuggestion.PLAN_AHEAD, review(weighIns, dailyIntake(lastWeek, MONDAY, 2300.0)).suggestion)
        assertEquals(ReviewSuggestion.EAT_ENOUGH, review(weighIns, dailyIntake(lastWeek, MONDAY, 1500.0)).suggestion)
    }

    @Test
    fun reachingTheTargetWeightComesFirst() {
        val result = review(weeklyWeighIns(78.6, 78.2), emptyList())
        assertEquals(ReviewSuggestion.GOAL_REACHED, result.suggestion)
    }

    @Test
    fun nothingLoggedGivesNoAverage() {
        val result = review(emptyList(), emptyList())
        assertNull(result.averageKcal)
        assertEquals(0, result.daysLogged)
    }
}
