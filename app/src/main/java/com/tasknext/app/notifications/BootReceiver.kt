package com.tasknext.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Rebuilds reminder alarms after reboot, app update, or clock/time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context)
                val tasks = AppDatabase.getInstance(context).taskDao().getAllOnce()
                tasks.forEach { task ->
                    if (task.slot != null && task.reminderEnabled && settings.notificationsEnabled) {
                        NotificationScheduler.schedule(
                            context,
                            task,
                            settings.defaultReminderHour,
                            settings.defaultReminderMinute
                        )
                    } else {
                        NotificationScheduler.cancel(context, task.id)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
