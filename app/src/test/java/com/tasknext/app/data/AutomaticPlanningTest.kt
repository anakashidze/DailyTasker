package com.tasknext.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class AutomaticPlanningTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: TaskRepository
    private val date get() = LocalDate.now()
    private val day get() = date.toEpochDay()
    private val dao get() = db.taskDao()

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context).apply { todayLockHour = 11; todayLockMinute = 0 }
        repo = TaskRepository(dao, settings)
    }
    @After fun cleanup() { db.close() }

    @Test fun rolloverSelectsFirstEligibleBigTaskInQueueOrder() = runBlocking {
        settings.lastActiveEpochDay = day - 1
        dao.insert(Task(title = "Small", taskSize = TaskSize.SMALL, orderIndex = 0))
        dao.insert(Task(title = "Overdue", orderIndex = 1, overdueSinceEpochDay = day - 2))
        dao.insert(Task(title = "Archived", orderIndex = 2, isArchived = true))
        val later = dao.insert(Task(title = "Later", orderIndex = 9))
        val first = dao.insert(Task(title = "First", orderIndex = 3))
        repo.rolloverDayIfNeeded(date.atStartOfDay())
        assertEquals(0, dao.getById(first)!!.slot)
        assertNull(dao.getById(later)!!.slot)
        assertEquals(1, dao.getAllOnce().count { it.slot == 0 })
    }

    @Test fun unfinishedYesterdayBecomesOverdueBeforeNextTaskIsSelected() = runBlocking {
        settings.lastActiveEpochDay = day - 1
        val old = dao.insert(Task(title = "Yesterday", slot = 0))
        val next = dao.insert(Task(title = "Next", orderIndex = 1))
        repo.rolloverDayIfNeeded(date.atStartOfDay())
        assertEquals(day - 1, dao.getById(old)!!.overdueSinceEpochDay)
        assertFalse(dao.getById(old)!!.reminderEnabled)
        assertEquals(0, dao.getById(next)!!.slot)
        assertEquals(DailyOutcomeStatus.MISSED, dao.getDailyOutcome(day - 1)?.status)
    }

    @Test fun lockFillsEmptyTodayUsingLatestOrderAndKeepsThatSelection() = runBlocking {
        settings.lastActiveEpochDay = day
        val first = dao.insert(Task(title = "First", orderIndex = 0))
        val second = dao.insert(Task(title = "Second", orderIndex = 1))
        repo.rolloverDayIfNeeded(date.atTime(10, 59))
        assertNull(dao.getById(first)!!.slot)
        repo.reorderPool(listOf(second, first))
        repo.rolloverDayIfNeeded(date.atTime(11, 0))
        repo.rolloverDayIfNeeded(date.atTime(11, 1))
        assertEquals(0, dao.getById(second)!!.slot)
        assertNull(dao.getById(first)!!.slot)
        assertEquals(day, settings.lastAutoLockEpochDay)
    }

    @Test fun emptyQueueAtLockDoesNotTakeATaskAddedLater() = runBlocking {
        settings.lastActiveEpochDay = day
        repo.rolloverDayIfNeeded(date.atTime(11, 0))
        val addedLater = dao.insert(Task(title = "Tomorrow"))
        repo.rolloverDayIfNeeded(date.atTime(12, 0))
        assertNull(dao.getById(addedLater)!!.slot)
    }

    @Test fun lockDoesNotReplaceAnExistingTodayTask() = runBlocking {
        settings.lastActiveEpochDay = day
        val chosen = dao.insert(Task(title = "Chosen", slot = 0, orderIndex = 9))
        val queued = dao.insert(Task(title = "First in queue", orderIndex = 0))
        repo.rolloverDayIfNeeded(date.atTime(11, 0))
        assertEquals(0, dao.getById(chosen)!!.slot)
        assertNull(dao.getById(queued)!!.slot)
    }

    @Test fun completedOrRestDayDoesNotConsumeAnotherTask() = runBlocking {
        val next = dao.insert(Task(title = "Next"))
        for (status in listOf(DailyOutcomeStatus.COMPLETED, DailyOutcomeStatus.REST)) {
            settings.lastActiveEpochDay = day - 1
            settings.lastAutoLockEpochDay = -1
            dao.upsertDailyOutcome(DailyOutcome(day, status))
            repo.rolloverDayIfNeeded(date.atTime(11, 0))
            assertNull(dao.getById(next)!!.slot)
            assertTrue(repo.isDayComplete())
        }
    }

    @Test fun concurrentReceiversOnlyPromoteOneTask() = runBlocking {
        settings.lastActiveEpochDay = day - 1
        repeat(3) { dao.insert(Task(title = "Task $it", orderIndex = it)) }
        List(4) { async { TaskRepository(dao, settings).rolloverDayIfNeeded(date.atTime(11, 0)) } }.awaitAll()
        assertEquals(1, dao.getAllOnce().count { it.slot == 0 })
        assertEquals(2, dao.getAllOnce().count { it.slot == null && it.overdueSinceEpochDay == null })
    }

    @Test fun delayedStartupStillSelectsFromQueueAfterLock() = runBlocking {
        settings.lastActiveEpochDay = day - 3
        val first = dao.insert(Task(title = "First"))
        val second = dao.insert(Task(title = "Second", orderIndex = 1))
        repo.rolloverDayIfNeeded(date.atTime(15, 0))
        assertEquals(0, dao.getById(first)!!.slot)
        assertNull(dao.getById(second)!!.slot)
        assertNull(dao.getById(second)!!.overdueSinceEpochDay)
        assertEquals(day, settings.lastAutoLockEpochDay)
    }
}
