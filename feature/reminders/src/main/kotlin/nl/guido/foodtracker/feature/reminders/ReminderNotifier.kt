package nl.guido.foodtracker.feature.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.ui.OpenScreen
import nl.guido.foodtracker.core.ui.Routes

/** Shows the reminder notification; tapping it opens Log food for that meal. */
internal object ReminderNotifier {
    private const val CHANNEL_ID = "daily-reminder"
    private const val NOTIFICATION_ID = 1001

    /** True when Android lets this app show notifications (permission given and not switched off). */
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // checked by canNotify just below
    fun show(context: Context, meal: Meal) {
        if (!canNotify(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminders_channel_description) },
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.reminders_ic_notification)
            .setContentTitle(context.getString(notifyTitle(meal)))
            .setContentText(context.getString(R.string.reminders_notify_body))
            .setContentIntent(openLogFood(context, meal))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission taken away in the meantime: nothing to show.
        }
    }

    /** Opens the app (MainActivity, found by package) with the Log food route; an open app gets it as a new intent. */
    private fun openLogFood(context: Context, meal: Meal): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(OpenScreen.EXTRA_ROUTE, Routes.logFood(meal))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
