package com.tasknext.app.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tasknext.app.MainActivity
import com.tasknext.app.TaskDestination
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class TaskNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var db: AppDatabase
    @Before fun setup() {
        rule.waitForIdle()
        val context = rule.activity.applicationContext
        context.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(context).apply {
            notificationsEnabled = false
            lastActiveEpochDay = LocalDate.now().toEpochDay()
            todayLockHour = 0
            todayLockMinute = 0
        }
        db = AppDatabase.getInstance(context)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @Test fun reminderOpensFromSettingsAndDoesNotReplayAfterDismissAndRecreate() {
        val id = runBlocking { db.taskDao().insert(Task(title = "Read this reminder", comment = "Full details", reminderEnabled = false)) }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.runOnIdle { rule.activity.onNewIntent(TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Full details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Task details").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Full details").assertDoesNotExist()
        // The same reminder can be opened again while the Activity is already running.
        rule.runOnIdle { rule.activity.onNewIntent(TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Full details").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Task details").assertIsDisplayed()
    }

    @Test fun lockedTaskIsReadableAndDeletedDestinationShowsExplanation() {
        val id = runBlocking { db.taskDao().insert(Task(title = "Locked main", slot = 0, reminderEnabled = false)) }
        rule.runOnIdle { rule.activity.onNewIntent(TaskDestination.intent(rule.activity, id)) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Today is locked. You can still read the details and complete the task from Today.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edit task").assertDoesNotExist()
        runBlocking { db.taskDao().delete(id) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("This task was completed or removed. Close this window to see Today.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edit task").assertDoesNotExist()
    }
}
