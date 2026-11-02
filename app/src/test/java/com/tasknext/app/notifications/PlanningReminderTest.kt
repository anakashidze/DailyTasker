package com.tasknext.app.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.tasknext.app.TaskDestination
import com.tasknext.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlanningReminderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private val date get() = LocalDate.now()
    private val day get() = date.toEpochDay()
    private val manager get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val dao get() = db.taskDao()
    @Before fun setup() {
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context).apply { lastActiveEpochDay = day; todayLockHour = 11; todayLockMinute = 0 }
    }
    @After fun cleanup() { db.close() }
    private suspend fun receive(action: String, hour: Int = 18, minute: Int = 0) {
        DailyPlanningReceiver().handleAction(context, action, settings, dao, date.atTime(hour, minute))
    }

    @Test fun zeroStreakNeverPostsRiskNotificationAndClearsAnOldOne() = runBlocking {
        context.getSystemService(NotificationManager::class.java).notify(9102, Notification.Builder(context).build())
        receive(NotificationScheduler.ACTION_STREAK_RISK)
        assertEquals(0, settings.streakCount)
        assertNull(manager.getNotification(9102))
    }
    @Test fun stalePositiveCacheDoesNotSendFalseRiskWarning() = runBlocking {
        settings.streakCount = 8
        dao.upsertDailyOutcome(DailyOutcome(day - 3, DailyOutcomeStatus.COMPLETED))
        receive(NotificationScheduler.ACTION_STREAK_RISK)
        assertNull(manager.getNotification(9102))
    }
    @Test fun existingUnprotectedStreakSendsWarningWithActualCount() = runBlocking {
        dao.upsertDailyOutcome(DailyOutcome(day - 1, DailyOutcomeStatus.COMPLETED))
        receive(NotificationScheduler.ACTION_STREAK_RISK)
        val notice = manager.getNotification(9102)
        assertNotNull(notice)
        assertTrue(notice.extras.getString(Notification.EXTRA_TEXT)!!.contains("1-day streak"))
    }
    @Test fun completedAndRestDaysSuppressRiskWarning() = runBlocking {
        dao.upsertDailyOutcome(DailyOutcome(day - 1, DailyOutcomeStatus.COMPLETED))
        for (status in listOf(DailyOutcomeStatus.COMPLETED, DailyOutcomeStatus.REST)) {
            dao.upsertDailyOutcome(DailyOutcome(day, status))
            receive(NotificationScheduler.ACTION_STREAK_RISK)
            assertNull(manager.getNotification(9102))
        }
    }
    @Test fun eveningReviewShowsQueuedTaskAndOpensQueue() = runBlocking {
        dao.insert(Task(title = "Today", slot = 0))
        dao.insert(Task(title = "Tomorrow first", orderIndex = 0))
        receive(NotificationScheduler.ACTION_PLAN_TOMORROW, 20)
        val notice = manager.getNotification(9101)
        assertTrue(notice.extras.getString(Notification.EXTRA_TEXT)!!.contains("Tomorrow first"))
        assertEquals(true, TaskDestination.request(shadowOf(notice.contentIntent).savedIntent)?.openQueue)
    }
    @Test fun preLockWarningIsNotDeliveredAfterTheDeadline() = runBlocking {
        dao.insert(Task(title = "Today", slot = 0))
        receive(NotificationScheduler.ACTION_BEFORE_LOCK, 10, 30)
        assertNotNull(manager.getNotification(9105))
        context.getSystemService(NotificationManager::class.java).cancel(9105)
        receive(NotificationScheduler.ACTION_BEFORE_LOCK, 11, 5)
        assertNull(manager.getNotification(9105))
    }
    @Test fun midnightLockWarnsAboutNextTaskEvenIfTodayIsComplete() = runBlocking {
        settings.todayLockHour = 0
        settings.dayCompletedEpochDay = day
        dao.upsertDailyOutcome(DailyOutcome(day, DailyOutcomeStatus.COMPLETED))
        dao.insert(Task(title = "Tomorrow task"))
        receive(NotificationScheduler.ACTION_BEFORE_LOCK, 23, 30)
        assertTrue(manager.getNotification(9105).extras.getString(Notification.EXTRA_TEXT)!!.contains("Tomorrow task"))
    }
    @Test fun disablingNotificationsKeepsPlanningAlarmsAndAutomaticSelection() = runBlocking {
        settings.notificationsEnabled = false
        settings.lastActiveEpochDay = day - 1
        val id = dao.insert(Task(title = "Automatic"))
        receive(NotificationScheduler.ACTION_DAY_START, 0)
        assertEquals(0, dao.getById(id)!!.slot)
        val actions = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .map { shadowOf(it.operation).savedIntent.action }.toSet()
        assertEquals(setOf(NotificationScheduler.ACTION_DAY_START, NotificationScheduler.ACTION_LOCK_TODAY), actions)
        assertEquals(0, manager.size())
    }
    @Test fun preLockAlarmTracksChangedTimeAndHandlesPreviousEvening() {
        assertEquals(date.atTime(10, 30), NotificationScheduler.nextPreLockReminder(settings, date.atTime(9, 0)))
        settings.todayLockHour = 12
        assertEquals(date.atTime(11, 30), NotificationScheduler.nextPreLockReminder(settings, date.atTime(9, 0)))
        settings.todayLockHour = 0
        assertEquals(date.atTime(23, 30), NotificationScheduler.nextPreLockReminder(settings, date.atTime(9, 0)))
    }
}
