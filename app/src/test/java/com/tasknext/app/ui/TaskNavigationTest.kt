package com.tasknext.app.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.tasknext.app.MainActivity
import com.tasknext.app.TaskDestination
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaskNavigationTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(context).apply {
            notificationsEnabled = false
            lastActiveEpochDay = LocalDate.now().toEpochDay()
            todayLockHour = 0
            todayLockMinute = 0
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", db)
        // ActivityScenario filters lifecycle events by the original Intent and loses
        // this Activity after onNewIntent/setIntent. Drive its lifecycle directly.
        controller = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
    }

    @After fun cleanup() {
        controller.pause().stop().destroy()
        db.close()
        ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    }

    private fun openReminder(id: Long) {
        rule.runOnIdle {
            // Android always pauses a resumed Activity before delivering onNewIntent.
            controller.pause().newIntent(TaskDestination.intent(activity, id)).resume()
        }
    }

    @Test fun reminderOpensFromSettingsAndDoesNotReplayAfterDismissAndRecreate() {
        val id = runBlocking(Dispatchers.IO) { db.taskDao().insert(Task(title = "Read this reminder", comment = "Full details", reminderEnabled = false)) }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Appearance").assertExists()
        openReminder(id)
        try {
            rule.waitUntil(5000) { rule.onAllNodesWithText("Full details").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            // Freeze automatic frame advancement to inspect the tree that keeps
            // the Robolectric idling loop busy; the original failure still fails.
            rule.mainClock.autoAdvance = false
            println("REMINDER TASK DB: " + runBlocking(Dispatchers.IO) { db.taskDao().getById(id) })
            println("REMINDER UI: " + rule.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 30))
            throw failure
        }
        rule.onNodeWithText("Task details").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        rule.runOnIdle { controller.recreate() }
        rule.onNodeWithText("Full details").assertDoesNotExist()
        // The same reminder can be opened again while the Activity is already running.
        openReminder(id)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Full details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Task details").assertIsDisplayed()
    }

    @Test fun lockedTaskIsReadableAndDeletedDestinationShowsExplanation() {
        val id = runBlocking(Dispatchers.IO) { db.taskDao().insert(Task(title = "Locked main", slot = 0, reminderEnabled = false)) }
        openReminder(id)
        try {
            rule.waitUntil(5000) { rule.onAllNodesWithText("Today is locked. You can still read the details and complete the task from Today.").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            println("LOCKED TASK DB: " + runBlocking(Dispatchers.IO) { db.taskDao().getById(id) })
            println("LOCK SETTINGS: " + SettingsStore(activity).todayLockHour + ":" + SettingsStore(activity).todayLockMinute)
            println("LOCKED TASK UI: " + rule.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 30))
            throw failure
        }
        rule.onNodeWithText("Edit task").assertDoesNotExist()
        runBlocking(Dispatchers.IO) { db.taskDao().delete(id) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("This task was completed or removed. Close this window to see Today.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edit task").assertDoesNotExist()
    }

    @Test fun reminderClosesTheTaskListDialogBeforeShowingItsDestination() {
        val id = runBlocking(Dispatchers.IO) { db.taskDao().insert(Task(title = "Requested task", comment = "Reminder details", reminderEnabled = false)) }
        rule.onNodeWithText("Tasks").performClick()
        rule.onNodeWithContentDescription("Add task").performClick()
        rule.onNodeWithText("Title").assertExists()
        openReminder(id)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Reminder details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Title").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
