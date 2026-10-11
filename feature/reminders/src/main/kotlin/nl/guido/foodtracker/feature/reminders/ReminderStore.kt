package nl.guido.foodtracker.feature.reminders

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Saves the reminder setting on the phone (not synced: each phone has its own reminder). */
@Singleton
class ReminderStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    fun save(value: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putInt(KEY_MINUTE_OF_DAY, minuteOfDay(value.time))
            .putInt(KEY_SECOND_MINUTE_OF_DAY, value.secondTime?.let(::minuteOfDay) ?: -1)
            .apply()
        _settings.value = value
    }

    private fun read(): ReminderSettings {
        val default = ReminderSettings()
        return ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, default.enabled),
            time = timeOf(prefs.getInt(KEY_MINUTE_OF_DAY, -1)) ?: default.time,
            secondTime = timeOf(prefs.getInt(KEY_SECOND_MINUTE_OF_DAY, -1)),
        )
    }

    private fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    private fun timeOf(minuteOfDay: Int): LocalTime? =
        if (minuteOfDay in 0 until 24 * 60) LocalTime.of(minuteOfDay / 60, minuteOfDay % 60) else null

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_MINUTE_OF_DAY = "minute_of_day"
        const val KEY_SECOND_MINUTE_OF_DAY = "second_minute_of_day"
    }
}
