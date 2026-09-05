package com.tasknext.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.tasknext.app.MainActivity
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task reminder"
        val settings = SettingsStore(context)
        if (!settings.notificationsEnabled) return

        showNotification(context, taskId, title, settings.snoozeMinutes)

        // A normal reminder repeats while the task remains active and reminders stay enabled.
        // A snooze alarm does not create another daily chain; the normal chain already exists.
        if (intent.action == NotificationScheduler.ACTION_REMINDER) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = AppDatabase.getInstance(context).taskDao().getById(taskId)
                    if (task != null && task.slot != null && task.reminderEnabled) {
                        NotificationScheduler.schedule(
                            context,
                            task,
                            settings.defaultReminderHour,
                            settings.defaultReminderMinute
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, taskId: Long, title: String, snoozeMinutes: Int) {
        val channelId = "tasknext_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
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
            .setContentTitle("Time for: $title")
            .setContentText("Tap to open TaskNext")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze ${snoozeMinutes.coerceAtLeast(1)} min", snoozeIntent)
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
