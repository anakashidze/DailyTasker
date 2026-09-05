package com.tasknext.app.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tasknext.app.data.SettingsStore

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_TITLE) ?: "Task reminder"
        val settings = SettingsStore(context)
        if (!settings.notificationsEnabled) return

        NotificationScheduler.scheduleSnooze(context, taskId, title, settings.snoozeMinutes)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(taskId.toInt())
    }
}
