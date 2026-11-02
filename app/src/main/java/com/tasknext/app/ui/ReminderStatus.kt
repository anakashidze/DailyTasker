package com.tasknext.app.ui

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tasknext.app.R
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import com.tasknext.app.notifications.NotificationScheduler
import java.time.LocalDateTime

internal fun notificationsAvailable(context: Context, channelId: String? = null): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val manager = context.getSystemService(NotificationManager::class.java)
    return (channelId?.let { listOf(it) } ?: listOf("tasknext_reminders", "tasknext_planning")).none {
        manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE
    }
}

@Composable
internal fun rememberNotificationAvailability(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var available by remember { mutableStateOf(notificationsAvailable(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) available = notificationsAvailable(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return available
}

internal fun taskReminderSummary(context: Context, task: Task, settings: SettingsStore, now: LocalDateTime): String {
    if (!settings.notificationsEnabled || !task.reminderEnabled) return context.getString(R.string.reminder_off)
    if (!notificationsAvailable(context, "tasknext_reminders")) return context.getString(R.string.notifications_blocked)
    if (task.slot == null || task.overdueSinceEpochDay != null) return context.getString(R.string.reminder_when_active)
    val hour = task.reminderHour ?: settings.defaultReminderHour
    val minute = task.reminderMinute ?: settings.defaultReminderMinute
    return if (NotificationScheduler.taskReminderTime(hour, minute, now) == null)
        context.getString(R.string.reminder_time_passed)
    else context.getString(R.string.reminder_today_at, formatAppTime(context, hour, minute))
}
