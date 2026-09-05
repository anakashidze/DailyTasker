package com.tasknext.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import java.util.Calendar

object NotificationScheduler {

    const val ACTION_REMINDER = "com.tasknext.app.action.REMINDER"
    const val ACTION_SNOOZE_ALARM = "com.tasknext.app.action.SNOOZE_ALARM"

    fun schedule(context: Context, task: Task, defaultHour: Int, defaultMinute: Int) {
        val settings = SettingsStore(context)
        if (!settings.notificationsEnabled || !task.reminderEnabled || task.slot == null) {
            cancel(context, task.id)
            return
        }

        val hour = task.reminderHour ?: defaultHour
        val minute = task.reminderMinute ?: defaultMinute
        scheduleAt(
            context = context,
            taskId = task.id,
            title = task.title,
            triggerAtMillis = nextOccurrence(hour, minute),
            action = ACTION_REMINDER
        )
    }

    fun scheduleSnooze(context: Context, taskId: Long, title: String, minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        scheduleAt(
            context = context,
            taskId = taskId,
            title = title,
            triggerAtMillis = System.currentTimeMillis() + safeMinutes * 60_000L,
            action = ACTION_SNOOZE_ALARM
        )
    }

    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, taskId, ACTION_REMINDER))
        alarmManager.cancel(pendingIntent(context, taskId, ACTION_SNOOZE_ALARM))
    }

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    private fun scheduleAt(
        context: Context,
        taskId: Long,
        title: String,
        triggerAtMillis: Long,
        action: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, taskId, action, title)

        // Exact-alarm access is commonly denied by default on modern Android.
        // Use it when granted, otherwise degrade gracefully to an inexact idle-safe alarm.
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(
        context: Context,
        taskId: Long,
        action: String,
        title: String? = null
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            if (title != null) putExtra(ReminderReceiver.EXTRA_TASK_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
