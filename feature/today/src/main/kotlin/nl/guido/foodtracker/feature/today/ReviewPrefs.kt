package nl.guido.foodtracker.feature.today

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers on this phone which Monday's review card was put away, so it stays away. */
@Singleton
internal class ReviewPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE)
    private val dismissed = MutableStateFlow(prefs.getString(KEY, null))

    /** The reviewed week (its weekStart, ISO date) whose card was put away, if any. */
    val dismissedOn: StateFlow<String?> = dismissed.asStateFlow()

    fun dismiss(weekStart: LocalDate) {
        prefs.edit().putString(KEY, weekStart.toString()).apply()
        dismissed.value = weekStart.toString()
    }

    private companion object {
        const val KEY = "review_dismissed_week"
    }
}
