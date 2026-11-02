package com.tasknext.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.max

object NotificationScheduler {

    const val ACTION_REMINDER = "com.tasknext.app.action.REMINDER"
    const val ACTION_SNOOZE_ALARM = "com.tasknext.app.action.SNOOZE_ALARM"
    const val ACTION_PLAN_TOMORROW = "com.tasknext.app.action.PLAN_TOMORROW"
    const val ACTION_STREAK_RISK = "com.tasknext.app.action.STREAK_RISK"
    const val ACTION_DAY_START = "com.tasknext.app.action.DAY_START"
    const val ACTION_LOCK_TODAY = "com.tasknext.app.action.LOCK_TODAY"
    const val ACTION_BEFORE_LOCK = "com.tasknext.app.action.BEFORE_LOCK"

    private const val PLAN_REQUEST_CODE = 9101
    private const val STREAK_REQUEST_CODE = 9102
    private const val DAY_START_REQUEST_CODE = 9103
    private const val LOCK_REQUEST_CODE = 9104
    private const val BEFORE_LOCK_REQUEST_CODE = 9105

    fun schedule(context: Context, task: Task, defaultHour: Int, defaultMinute: Int) {
        val settings = SettingsStore(context)
        if (!settings.notificationsEnabled || !task.reminderEnabled || task.slot == null ||
            task.isArchived || task.overdueSinceEpochDay != null) {
            cancel(context, task.id)
            return
        }

        val hour = task.reminderHour ?: defaultHour
        val minute = task.reminderMinute ?: defaultMinute
        val time = taskReminderTime(hour, minute) ?: run {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.cancel(taskPendingIntent(context, task.id, ACTION_REMINDER))
            return
        }
        scheduleTaskAt(
            context = context,
            taskId = task.id,
            title = task.title,
            triggerAtMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            action = ACTION_REMINDER
        )
    }

    fun scheduleSnooze(context: Context, taskId: Long, title: String, minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        scheduleTaskAt(
            context = context,
            taskId = taskId,
            title = title,
            triggerAtMillis = System.currentTimeMillis() + safeMinutes * 60_000L,
            action = ACTION_SNOOZE_ALARM
        )
    }

    fun syncDailyReminders(
        context: Context,
        settings: SettingsStore,
        todayTask: Task?,
        onlyAction: String? = null
    ) {
        // Planning is app behavior, independent of notification permission/preferences.
        if (onlyAction == null || onlyAction == ACTION_DAY_START) {
            scheduleDailyAction(context, ACTION_DAY_START, DAY_START_REQUEST_CODE, nextOccurrence(0, 0))
        }
        if (onlyAction == null || onlyAction == ACTION_LOCK_TODAY) {
            scheduleDailyAction(context, ACTION_LOCK_TODAY, LOCK_REQUEST_CODE,
                nextOccurrence(settings.todayLockHour, settings.todayLockMinute))
        }
        if (!settings.notificationsEnabled) {
            cancelDailyReminders(context)
            return
        }

        if (onlyAction == null || onlyAction == ACTION_BEFORE_LOCK) {
            scheduleDailyAction(context, ACTION_BEFORE_LOCK, BEFORE_LOCK_REQUEST_CODE,
                nextPreLockReminder(settings).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }

        if (onlyAction == null || onlyAction == ACTION_PLAN_TOMORROW) {
            if (settings.planTomorrowReminderEnabled) {
                scheduleDailyAction(
                    context = context,
                    action = ACTION_PLAN_TOMORROW,
                    requestCode = PLAN_REQUEST_CODE,
                    triggerAtMillis = nextOccurrence(
                        settings.planTomorrowReminderHour,
                        settings.planTomorrowReminderMinute
                    )
                )
            } else {
                cancelDailyAction(context, ACTION_PLAN_TOMORROW, PLAN_REQUEST_CODE)
            }

        }
        if (onlyAction == null || onlyAction == ACTION_STREAK_RISK) {
            if (settings.streakRiskReminderEnabled && settings.streakCount > 0) {
                val preferredMinutes =
                    settings.streakRiskReminderHour * 60 + settings.streakRiskReminderMinute

                val taskReminderMinutes = if (todayTask?.reminderEnabled == true) {
                    val hour = todayTask.reminderHour ?: settings.defaultReminderHour
                    val minute = todayTask.reminderMinute ?: settings.defaultReminderMinute
                    hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
                } else {
                    -1
                }

                val effectiveMinutes = max(
                    preferredMinutes,
                    if (taskReminderMinutes >= 0) taskReminderMinutes + 15 else preferredMinutes
                ).coerceAtMost(23 * 60 + 59)

                scheduleDailyAction(
                    context = context,
                    action = ACTION_STREAK_RISK,
                    requestCode = STREAK_REQUEST_CODE,
                    triggerAtMillis = nextOccurrence(
                        effectiveMinutes / 60,
                        effectiveMinutes % 60
                    )
                )
            } else {
                cancelDailyAction(context, ACTION_STREAK_RISK, STREAK_REQUEST_CODE)
                context.getSystemService(NotificationManager::class.java).cancel(STREAK_REQUEST_CODE)
            }
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(taskPendingIntent(context, taskId, ACTION_REMINDER))
        alarmManager.cancel(taskPendingIntent(context, taskId, ACTION_SNOOZE_ALARM))
    }

    fun cancelDailyReminders(context: Context) {
        cancelDailyAction(context, ACTION_PLAN_TOMORROW, PLAN_REQUEST_CODE)
        cancelDailyAction(context, ACTION_STREAK_RISK, STREAK_REQUEST_CODE)
        cancelDailyAction(context, ACTION_BEFORE_LOCK, BEFORE_LOCK_REQUEST_CODE)
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(PLAN_REQUEST_CODE, STREAK_REQUEST_CODE, BEFORE_LOCK_REQUEST_CODE).forEach { manager.cancel(it) }
    }

    internal fun upcomingLock(settings: SettingsStore, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val lock = now.toLocalDate().atTime(settings.todayLockHour, settings.todayLockMinute)
        return if (lock.isAfter(now)) lock else lock.plusDays(1)
    }

    internal fun nextPreLockReminder(settings: SettingsStore, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val reminder = upcomingLock(settings, now).minusMinutes(30)
        return if (reminder.isAfter(now)) reminder else reminder.plusDays(1)
    }

    private fun cancelDailyAction(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(dailyPendingIntent(context, action, requestCode))
    }

    internal fun taskReminderTime(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val todayTime = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return todayTime.takeIf { it.isAfter(now) }
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

    private fun scheduleTaskAt(
        context: Context,
        taskId: Long,
        title: String,
        triggerAtMillis: Long,
        action: String
    ) {
        scheduleAlarm(
            context,
            triggerAtMillis,
            taskPendingIntent(context, taskId, action, title)
        )
    }

    private fun scheduleDailyAction(
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long
    ) {
        scheduleAlarm(
            context,
            triggerAtMillis,
            dailyPendingIntent(context, action, requestCode)
        )
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun taskPendingIntent(
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

    private fun dailyPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, DailyPlanningReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
