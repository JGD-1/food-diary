package nl.guido.foodtracker.feature.today

import nl.guido.foodtracker.core.model.ReviewSuggestion
import nl.guido.foodtracker.core.model.WeeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyReviewTest {

    private val lastWeek = WeeklyReview(
        weekStart = TODAY.minusWeeks(1),
        averageKcal = 2050,
        daysLogged = 6,
        targetKcal = 2100,
        weighInKg = 82.1,
        changeKg = -0.4,
        targetWeightKg = 75.0,
        suggestion = ReviewSuggestion.KEEP_GOING,
    )

    @Test
    fun `last week's review shows on Monday and the rest of the week`() {
        assertEquals(lastWeek, reviewToShow(TODAY, lastWeek, null))
        assertEquals(lastWeek, reviewToShow(TODAY.plusDays(6), lastWeek, null))
    }

    @Test
    fun `an older review does not show`() {
        assertNull(reviewToShow(TODAY.plusDays(7), lastWeek, null))
    }

    @Test
    fun `put away stays away`() {
        assertNull(reviewToShow(TODAY.plusDays(2), lastWeek, lastWeek.weekStart.toString()))
    }

    @Test
    fun `nothing to look back on shows nothing`() {
        val empty = lastWeek.copy(averageKcal = null, daysLogged = 0, weighInKg = null, changeKg = null)
        assertNull(reviewToShow(TODAY, empty, null))
        assertNull(reviewToShow(TODAY, null, null))
    }
}
