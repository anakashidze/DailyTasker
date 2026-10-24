package com.tasknext.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreakTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repository: TaskRepository
    private val today get() = LocalDate.now().toEpochDay()
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
        repository = TaskRepository(db.taskDao(), settings)
    }
    @After fun cleanup() { db.close() }
    private suspend fun outcome(daysAgo: Long, status: DailyOutcomeStatus, taskId: Long? = null) {
        db.taskDao().upsertDailyOutcome(DailyOutcome(today - daysAgo, status, taskId))
    }
    @Test fun restYesterdayPreservesStreakOnRollover() = runBlocking {
        outcome(3, DailyOutcomeStatus.COMPLETED)
        outcome(2, DailyOutcomeStatus.COMPLETED)
        outcome(1, DailyOutcomeStatus.REST)
        settings.streakCount = 2
        settings.streakLastCompletedEpochDay = today - 2
        settings.lastActiveEpochDay = today - 1
        settings.dayCompletedEpochDay = today - 1
        repository.rolloverDayIfNeeded()
        assertEquals(2, repository.currentStreakCount())
        assertEquals(DailyOutcomeStatus.REST, db.taskDao().getDailyOutcome(today - 1)?.status)
    }
    @Test fun consecutiveProtectedDaysDoNotAddToStreak() = runBlocking {
        outcome(3, DailyOutcomeStatus.COMPLETED)
        outcome(2, DailyOutcomeStatus.REST)
        outcome(1, DailyOutcomeStatus.REST)
        outcome(0, DailyOutcomeStatus.REST)
        assertEquals(1, repository.currentStreakCount())
    }
    @Test fun restWithoutAnyCompletionDoesNotCreateAStreak() = runBlocking {
        outcome(0, DailyOutcomeStatus.REST)
        settings.dayCompletedEpochDay = today
        assertEquals(0, repository.currentStreakCount())
    }
    @Test fun missingOrMissedDayBreaksContinuity() = runBlocking {
        outcome(3, DailyOutcomeStatus.COMPLETED)
        outcome(1, DailyOutcomeStatus.REST)
        assertEquals(0, repository.currentStreakCount())
        outcome(2, DailyOutcomeStatus.MISSED)
        assertEquals(0, repository.currentStreakCount())
    }
    @Test fun completingAfterRestExtendsOnlyTheCompletedCount() = runBlocking {
        outcome(2, DailyOutcomeStatus.COMPLETED)
        outcome(1, DailyOutcomeStatus.REST)
        val id = db.taskDao().insert(Task(title = "Today", slot = 0))
        repository.completeMandatory(id)
        assertEquals(2, repository.currentStreakCount())
    }
    @Test fun undoBonusDoesNotEraseLaterMainCompletion() = runBlocking {
        val bonus = Task(id = db.taskDao().insert(Task(title = "Bonus", taskSize = TaskSize.SMALL, slot = 1)),
            title = "Bonus", taskSize = TaskSize.SMALL, slot = 1)
        repository.completeBonus(bonus.id)
        val main = db.taskDao().insert(Task(title = "Today", slot = 0))
        repository.completeMandatory(main)
        repository.undoCompletion(bonus, -1L, -1L, 0, -1L)
        assertTrue(repository.isDayComplete())
        assertEquals(1, repository.currentStreakCount())
        assertFalse(db.taskDao().getById(bonus.id)!!.isArchived)
        assertEquals(0, db.taskDao().countSmallTaskCompletions(today, today))
    }
    @Test fun noonLockCannotSaveMinutesAfterNoon() {
        settings.todayLockHour = 12
        settings.todayLockMinute = 45
        assertEquals(0, settings.todayLockMinute)
    }
}
