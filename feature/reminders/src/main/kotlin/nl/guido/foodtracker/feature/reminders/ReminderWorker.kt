package nl.guido.foodtracker.feature.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import java.time.LocalDate

/** The daily job: shows "Log lunch?" unless that meal is already logged, then plans tomorrow's job. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** How the job reaches the app's shared parts (WorkManager creates it, not Hilt). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun reminderStore(): ReminderStore
        fun diaryRepository(): DiaryRepository
        fun sessionRepository(): SessionRepository
    }

    override suspend fun doWork(): Result {
        val parts = EntryPointAccessors.fromApplication(applicationContext, Parts::class.java)
        val settings = parts.reminderStore().settings.value
        if (!settings.enabled) return Result.success()
        // Plan tomorrow first, so a problem below never stops the reminder for good.
        ReminderScheduler.planNext(applicationContext, settings)
        val meal = mealForTime(settings.time)
        val logged = runCatching {
            val userId = parts.sessionRepository().currentUser.value.userId
            parts.diaryRepository().entries(userId, LocalDate.now()).first()
        }.getOrDefault(emptyList())
        if (shouldRemind(logged, meal)) ReminderNotifier.show(applicationContext, meal)
        return Result.success()
    }
}
