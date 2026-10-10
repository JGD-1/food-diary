package nl.guido.foodtracker.feature.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plans the reminder with WorkManager (Android's helper for background jobs; it keeps them after a restart).
 * One job at a time: it shows the notification and then plans the next day's job.
 */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /** Called when the setting changes: replaces any planned reminder, or removes it when switched off. */
    fun apply(settings: ReminderSettings) {
        if (settings.enabled) {
            enqueue(context, settings, ExistingWorkPolicy.REPLACE)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        internal const val WORK_NAME = "daily-reminder"

        /** From inside the running job: queue tomorrow's after this one, without stopping this one. */
        internal fun planNext(context: Context, settings: ReminderSettings) =
            enqueue(context, settings, ExistingWorkPolicy.APPEND_OR_REPLACE)

        private fun enqueue(context: Context, settings: ReminderSettings, policy: ExistingWorkPolicy) {
            val delay = delayUntilNext(LocalDateTime.now(), settings.time)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
