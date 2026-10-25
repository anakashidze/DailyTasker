package com.tasknext.app.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tasknext.app.MainActivity
import com.tasknext.app.TaskDestination
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaskNavigationTest {
    val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var db: AppDatabase
    // Prepare application state before ActivityScenario launches MainActivity. Each
    // test owns a fresh Room instance rather than a singleton from another sandbox.
    @get:Rule val fixture = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
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
        }
        override fun after() {
            db.close()
            ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
        }
    }).around(rule)

    @Test(timeout = 60000) fun reminderOpensFromSettingsAndDoesNotReplayAfterDismissAndRecreate() {
        val id = runBlocking(Dispatchers.IO) { db.taskDao().insert(Task(title = "Read this reminder", comment = "Full details", reminderEnabled = false)) }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.runOnIdle { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Full details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Task details").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        rule.runOnIdle { rule.activity.recreate() }
        rule.onNodeWithText("Full details").assertDoesNotExist()
        // The same reminder can be opened again while the Activity is already running.
        rule.runOnIdle { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Full details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Task details").assertIsDisplayed()
    }

    @Test(timeout = 60000) fun lockedTaskIsReadableAndDeletedDestinationShowsExplanation() {
        val id = runBlocking(Dispatchers.IO) { db.taskDao().insert(Task(title = "Locked main", slot = 0, reminderEnabled = false)) }
        rule.runOnIdle { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Today is locked. You can still read the details and complete the task from Today.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edit task").assertDoesNotExist()
        runBlocking(Dispatchers.IO) { db.taskDao().delete(id) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("This task was completed or removed. Close this window to see Today.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edit task").assertDoesNotExist()
    }

    @Test(timeout = 60000) fun reminderClosesTheTaskListDialogBeforeShowingItsDestination() {
        val id = runBlocking(Dispatchers.IO) { db.taskDao().insert(Task(title = "Requested task", comment = "Reminder details", reminderEnabled = false)) }
        rule.onNodeWithText("Tasks").performClick()
        rule.onNodeWithContentDescription("Add task").performClick()
        rule.onNodeWithText("Title").assertExists()
        rule.runOnIdle { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Reminder details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Title").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
