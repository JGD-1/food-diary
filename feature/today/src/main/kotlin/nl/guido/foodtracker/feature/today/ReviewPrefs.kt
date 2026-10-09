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

    /** The Monday (ISO date) whose review was put away, if any. */
    val dismissedOn: StateFlow<String?> = dismissed.asStateFlow()

    fun dismiss(monday: LocalDate) {
        prefs.edit().putString(KEY, monday.toString()).apply()
        dismissed.value = monday.toString()
    }

    private companion object {
        const val KEY = "review_dismissed_on"
    }
}
