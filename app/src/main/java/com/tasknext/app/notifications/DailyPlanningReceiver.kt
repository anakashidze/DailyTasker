package com.tasknext.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.tasknext.app.TaskDestination
import com.tasknext.app.R
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.DailyOutcomeStatus
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.TaskRepository
import com.tasknext.app.data.TaskSize
import com.tasknext.app.data.TaskDao
import com.tasknext.app.ui.formatAppTime
import com.tasknext.app.widget.TodayTaskWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale

class DailyPlanningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(context, intent.action, SettingsStore(context), AppDatabase.getInstance(context).taskDao())
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun handleAction(
        context: Context,
        action: String?,
        settings: SettingsStore,
        dao: TaskDao,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val repo = TaskRepository(dao, settings)
        repo.rolloverDayIfNeeded(now)
        val streak = repo.currentStreakCount()
        val tasks = dao.getAllOnce()
        val todayTask = tasks.firstOrNull { it.slot == 0 && it.overdueSinceEpochDay == null }
        val nextTask = tasks.firstOrNull {
            it.taskSize == TaskSize.BIG && it.slot == null && it.overdueSinceEpochDay == null
        }
        val today = now.toLocalDate().toEpochDay()
        val protected = dao.getDailyOutcome(today)?.status in
            setOf(DailyOutcomeStatus.COMPLETED, DailyOutcomeStatus.REST) || settings.dayCompletedEpochDay == today
        val manager = context.getSystemService(NotificationManager::class.java)
        if (streak == 0 || protected || !settings.notificationsEnabled || !settings.streakRiskReminderEnabled) {
            manager.cancel(STREAK_NOTIFICATION_ID)
        }

        if (settings.notificationsEnabled) {
            val lc = localizedContext(context, settings)
            val lockTime = formatAppTime(lc, settings.todayLockHour, settings.todayLockMinute)
            when (action) {
                NotificationScheduler.ACTION_PLAN_TOMORROW -> if (settings.planTomorrowReminderEnabled) {
                    val body = nextTask?.let {
                        lc.getString(R.string.notification_plan_queue_body, it.title, lockTime)
                    } ?: lc.getString(R.string.notification_plan_body, lockTime)
                    showPlanningNotification(context, settings, PLAN_NOTIFICATION_ID,
                        R.string.notification_plan_title, body, openQueue = true)
                }
                NotificationScheduler.ACTION_BEFORE_LOCK -> {
                    val lock = NotificationScheduler.upcomingLock(settings, now)
                    val sameDay = lock.toLocalDate() == now.toLocalDate()
                    // Suppress a delayed alarm after its window; do not announce tomorrow's
                    // lock in the morning. Midnight locks correctly warn the evening before.
                    if (!now.isBefore(lock.minusMinutes(30)) && (!sameDay || !protected)) {
                        val candidate = if (sameDay) todayTask ?: nextTask else nextTask
                        val body = candidate?.let {
                            lc.getString(R.string.notification_before_lock_body, it.title, lockTime)
                        } ?: lc.getString(R.string.notification_before_lock_empty, lockTime)
                        showPlanningNotification(context, settings, BEFORE_LOCK_NOTIFICATION_ID,
                            R.string.notification_before_lock_title, body, openQueue = true)
                    }
                }
                NotificationScheduler.ACTION_STREAK_RISK -> if (settings.streakRiskReminderEnabled && streak > 0 && !protected) {
                    showPlanningNotification(context, settings, STREAK_NOTIFICATION_ID,
                        R.string.notification_streak_title, lc.getString(R.string.notification_streak_body, streak))
                }
            }
        }

        // Auto-selected tasks get their own reminder even if the app stays closed.
        tasks.forEach { NotificationScheduler.schedule(context, it, settings.defaultReminderHour, settings.defaultReminderMinute) }
        val planningEvent = action == NotificationScheduler.ACTION_DAY_START || action == NotificationScheduler.ACTION_LOCK_TODAY
        NotificationScheduler.syncDailyReminders(context, settings, todayTask, onlyAction = if (planningEvent) null else action)
        if (planningEvent) manager.cancel(BEFORE_LOCK_NOTIFICATION_ID)
        TodayTaskWidget.requestUpdate(context)
    }

    private fun localizedContext(context: Context, settings: SettingsStore): Context {
        val locale = Locale.forLanguageTag(settings.languageCode)
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
    }

    internal fun showPlanningNotification(
        context: Context,
        settings: SettingsStore,
        notificationId: Int,
        titleRes: Int,
        body: String,
        openQueue: Boolean = false
    ) {
        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val lc = localizedContext(context, settings)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tasknext_planning"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                lc.getString(R.string.notification_planning_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            TaskDestination.intent(context, openQueue = openQueue),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(lc.getString(titleRes))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        private const val PLAN_NOTIFICATION_ID = 9101
        private const val STREAK_NOTIFICATION_ID = 9102
        private const val BEFORE_LOCK_NOTIFICATION_ID = 9105
    }
}
