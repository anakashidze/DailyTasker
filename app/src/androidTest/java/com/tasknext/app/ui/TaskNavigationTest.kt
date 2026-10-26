package com.tasknext.app.ui

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tasknext.app.MainActivity
import com.tasknext.app.TaskDestination
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Exercise real Android Intent delivery, navigation transitions and recreation. */
@RunWith(AndroidJUnit4::class)
class TaskNavigationTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setup() {
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(context).apply {
            notificationsEnabled = false
            lastActiveEpochDay = LocalDate.now().toEpochDay()
            todayLockHour = 0
            todayLockMinute = 0
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        setDatabase(db)
    }

    @After fun cleanup() {
        scenario?.close()
        db.close()
        setDatabase(null)
    }

    private fun setDatabase(database: AppDatabase?) {
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, database)
        }
    }

    private fun launchTask(id: Long) {
        // Keep the same real reminder identity for warm launches. ActivityScenario
        // matches lifecycle callbacks by the original Intent's action and data.
        scenario = ActivityScenario.launch(TaskDestination.intent(context, id))
    }

    private fun openReminder(id: Long) {
        PendingIntent.getActivity(context, id.toInt(), TaskDestination.intent(context, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).send()
    }

    private fun awaitText(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun reminderColdStartOpensTheRequestedTask() {
        val id = runBlocking(Dispatchers.IO) {
            db.taskDao().insert(Task(title = "Cold launch task", comment = "Cold launch details", reminderEnabled = false))
        }
        launchTask(id)
        awaitText("Cold launch details")
        rule.onNodeWithText("Task details").assertIsDisplayed()
    }

    @Test fun reminderOpensFromSettingsAndDoesNotReplayAfterDismissAndRecreate() {
        val id = runBlocking(Dispatchers.IO) {
            db.taskDao().insert(Task(title = "Read this reminder", comment = "Full details", reminderEnabled = false))
        }
        launchTask(id)
        awaitText("Full details")
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").assertExists()
        openReminder(id)
        awaitText("Full details")
        rule.onNodeWithText("Task details").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        scenario!!.recreate()
        rule.onNodeWithText("Full details").assertDoesNotExist()
        openReminder(id)
        awaitText("Full details")
        rule.onNodeWithText("Task details").assertIsDisplayed()
    }

    @Test fun lockedTaskIsReadableAndDeletedDestinationShowsExplanation() {
        val id = runBlocking(Dispatchers.IO) {
            db.taskDao().insert(Task(title = "Locked main", slot = 0, reminderEnabled = false))
        }
        launchTask(id)
        awaitText("Today is locked. You can still read the details and complete the task from Today.")
        rule.onNodeWithText("Edit task").assertDoesNotExist()
        runBlocking(Dispatchers.IO) { db.taskDao().delete(id) }
        awaitText("This task was completed or removed. Close this window to see Today.")
        rule.onNodeWithText("Edit task").assertDoesNotExist()
    }

    @Test fun reminderClosesTheTaskListDialogBeforeShowingItsDestination() {
        val id = runBlocking(Dispatchers.IO) {
            db.taskDao().insert(Task(title = "Requested task", comment = "Reminder details", reminderEnabled = false))
        }
        launchTask(id)
        awaitText("Reminder details")
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Tasks").performClick()
        rule.onNodeWithContentDescription("Add task").performClick()
        rule.onNodeWithText("Title").assertExists()
        openReminder(id)
        awaitText("Reminder details")
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Title").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
