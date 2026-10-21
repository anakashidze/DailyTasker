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
import com.tasknext.app.MainActivity
import com.tasknext.app.R
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.DailyOutcomeStatus
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.TaskRepository
import com.tasknext.app.data.TaskSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class DailyPlanningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context)
                if (!settings.notificationsEnabled) return@launch

                val dao = AppDatabase.getInstance(context).taskDao()
                val repo = TaskRepository(dao, settings)
                repo.rolloverDayIfNeeded()
                val tasks = dao.getAllOnce()
                val todayTask = tasks.firstOrNull {
                    !it.isArchived && it.slot == 0 && it.overdueSinceEpochDay == null
                }

                when (intent.action) {
                    NotificationScheduler.ACTION_PLAN_TOMORROW -> {
                        if (settings.planTomorrowReminderEnabled) {
                            val hasTomorrowBig = tasks.any {
                                !it.isArchived &&
                                    it.taskSize == TaskSize.BIG &&
                                    it.slot == null &&
                                    it.overdueSinceEpochDay == null
                            }
                            if (!hasTomorrowBig) {
                                showPlanningNotification(
                                    context,
                                    settings,
                                    notificationId = PLAN_NOTIFICATION_ID,
                                    titleRes = R.string.notification_plan_title,
                                    body = localizedContext(context, settings)
                                        .getString(R.string.notification_plan_body)
                                )
                            }
                        }
                    }

                    NotificationScheduler.ACTION_STREAK_RISK -> {
                        if (settings.streakRiskReminderEnabled) {
                            val today = LocalDate.now().toEpochDay()
                            val outcome = dao.getDailyOutcome(today)
                            val protected = outcome?.status == DailyOutcomeStatus.COMPLETED ||
                                outcome?.status == DailyOutcomeStatus.REST ||
                                settings.dayCompletedEpochDay == today

                            if (!protected) {
                                val lc = localizedContext(context, settings)
                                val body = if (settings.streakCount > 0) {
                                    lc.getString(
                                        R.string.notification_streak_body,
                                        settings.streakCount
                                    )
                                } else {
                                    lc.getString(R.string.notification_today_open_body)
                                }
                                showPlanningNotification(
                                    context,
                                    settings,
                                    notificationId = STREAK_NOTIFICATION_ID,
                                    titleRes = R.string.notification_streak_title,
                                    body = body
                                )
                            }
                        }
                    }
                }

                // Every daily alarm schedules its next occurrence after firing.
                NotificationScheduler.syncDailyReminders(context, settings, todayTask)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun localizedContext(context: Context, settings: SettingsStore): Context {
        val locale = Locale.forLanguageTag(settings.languageCode)
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
    }

    private fun showPlanningNotification(
        context: Context,
        settings: SettingsStore,
        notificationId: Int,
        titleRes: Int,
        body: String
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
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
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
    }
}
