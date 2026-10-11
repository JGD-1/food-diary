package nl.guido.foodtracker.feature.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plans the reminders with WorkManager (Android's helper for background jobs; it keeps them after a restart).
 * One job per reminder time ("slot"): it shows the notification and then plans that slot's next day.
 */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /** Called when the setting changes: replaces any planned reminders, or removes them when switched off. */
    fun apply(settings: ReminderSettings) {
        val workManager = WorkManager.getInstance(context)
        for (slot in 0 until ReminderSettings.SLOTS) {
            val time = settings.timeFor(slot)
            if (settings.enabled && time != null) {
                enqueue(context, slot, time, ExistingWorkPolicy.REPLACE)
            } else {
                workManager.cancelUniqueWork(workName(slot))
            }
        }
    }

    companion object {
        internal const val KEY_SLOT = "slot"

        /** The first slot keeps the name it had when there was only one reminder, so planned jobs carry over. */
        internal fun workName(slot: Int): String = if (slot == 0) "daily-reminder" else "daily-reminder-${slot + 1}"

        /** From inside the running job: queue the next day's after this one, without stopping this one. */
        internal fun planNext(context: Context, slot: Int, time: LocalTime) =
            enqueue(context, slot, time, ExistingWorkPolicy.APPEND_OR_REPLACE)

        private fun enqueue(context: Context, slot: Int, time: LocalTime, policy: ExistingWorkPolicy) {
            val delay = delayUntilNext(LocalDateTime.now(), time)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SLOT to slot))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(slot), policy, request)
        }
    }
}
