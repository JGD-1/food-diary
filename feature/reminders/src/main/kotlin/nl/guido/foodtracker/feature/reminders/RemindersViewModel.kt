package nl.guido.foodtracker.feature.reminders

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
internal class RemindersViewModel @Inject constructor(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val settings: StateFlow<ReminderSettings> = store.settings

    fun setEnabled(enabled: Boolean) = update(settings.value.copy(enabled = enabled))

    fun setTime(time: LocalTime) = update(settings.value.copy(time = time))

    private fun update(value: ReminderSettings) {
        store.save(value)
        scheduler.apply(value)
    }
}
