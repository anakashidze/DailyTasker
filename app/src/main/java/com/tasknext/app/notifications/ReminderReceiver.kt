package com.tasknext.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.tasknext.app.R
import com.tasknext.app.data.AppLocale
import com.tasknext.app.data.TaskRepository
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.tasknext.app.TaskDestination
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context)
                val dao = AppDatabase.getInstance(context).taskDao()
                TaskRepository(dao, settings).rolloverDayIfNeeded()
                val task = dao.getById(taskId)
                if (!settings.notificationsEnabled || task == null || task.isArchived ||
                    task.slot == null || task.overdueSinceEpochDay != null || !task.reminderEnabled
                ) {
                    NotificationScheduler.cancel(context, taskId)
                    return@launch
                }
                // Read the live task before delivery; stale alarms and snoozes must not
                // resurrect completed/deleted tasks or use an old title.
                showNotification(context, taskId, task.title, settings.snoozeMinutes)
                if (intent.action == NotificationScheduler.ACTION_REMINDER) {
                    NotificationScheduler.schedule(context, task, settings.defaultReminderHour, settings.defaultReminderMinute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal fun showNotification(context: Context, taskId: Long, title: String, snoozeMinutes: Int) {
        val lc = AppLocale.localizedContext(context)
        val channelId = "tasknext_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            lc.getString(R.string.notification_task_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            TaskDestination.intent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, SnoozeReceiver::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(lc.getString(R.string.notification_task_title, title))
            .setContentText(lc.getString(R.string.notification_task_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, lc.getString(R.string.notification_task_snooze, snoozeMinutes.coerceIn(1, 1440)), snoozeIntent)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(taskId.toInt(), notification)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
    }
}
