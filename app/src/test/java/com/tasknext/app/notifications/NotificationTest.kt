package com.tasknext.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.tasknext.app.R
import com.tasknext.app.data.SettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test @Config(sdk = [28]) fun dailyAndTaskNotificationsWorkOnOlderAndroid() {
        val settings = SettingsStore(context)
        DailyPlanningReceiver().showPlanningNotification(context, settings, 9101, R.string.notification_plan_title, "Body")
        ReminderReceiver().showNotification(context, 15L, "Test task", 10)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertNotNull(manager.getNotification(9101))
        assertNotNull(manager.getNotification(15))
    }

    @Test @Config(sdk = [33]) fun permissionDenialPreventsBothNotificationTypes() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        DailyPlanningReceiver().showPlanningNotification(context, SettingsStore(context), 9101, R.string.notification_plan_title, "Body")
        ReminderReceiver().showNotification(context, 15L, "Test task", 10)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertNull(manager.getNotification(9101))
        assertNull(manager.getNotification(15))
    }

    @Test @Config(sdk = [28]) fun taskReminderUsesTheChosenLanguage() {
        SettingsStore(context).languageCode = "ru"
        ReminderReceiver().showNotification(context, 15L, "Test task", 10)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val notification = manager.getNotification(15)
        assertEquals("Пора: Test task", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Отложить на 10 мин", notification.actions[0].title.toString())
    }

    @Test @Config(sdk = [28]) fun rearmingOneDailyReminderLeavesTheOtherAlarmUntouched() {
        val settings = SettingsStore(context)
        settings.planTomorrowReminderHour = 20
        settings.streakRiskReminderHour = 20
        NotificationScheduler.syncDailyReminders(context, settings, null)
        val manager = shadowOf(context.getSystemService(AlarmManager::class.java))
        val before = manager.scheduledAlarms.single {
            shadowOf(it.operation).savedIntent.action == NotificationScheduler.ACTION_STREAK_RISK
        }
        NotificationScheduler.syncDailyReminders(context, settings, null, onlyAction = NotificationScheduler.ACTION_PLAN_TOMORROW)
        val after = manager.scheduledAlarms.single {
            shadowOf(it.operation).savedIntent.action == NotificationScheduler.ACTION_STREAK_RISK
        }
        assertSame(before, after)
        assertEquals(2, manager.scheduledAlarms.size)
    }
}
