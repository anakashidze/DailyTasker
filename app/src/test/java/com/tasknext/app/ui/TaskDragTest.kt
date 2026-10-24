package com.tasknext.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import com.tasknext.app.ui.theme.TaskNextTheme
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class TaskDragTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var ids: List<Long>

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication() as Application
        app.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(app).apply {
            notificationsEnabled = false
            lastActiveEpochDay = LocalDate.now().toEpochDay()
        }
        db = AppDatabase.getInstance(app)
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        ids = runBlocking {
            (1..3).map { db.taskDao().insert(Task(title = "Task $it", orderIndex = it, reminderEnabled = false)) }
        }
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[TaskViewModel::class.java]
        rule.setContent { TaskNextTheme { TaskListScreen(vm, {}) } }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("task-card-${ids[0]}").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { store.clear() }

    private fun dragFirst(): Pair<Float, Float> {
        val node = rule.onNodeWithTag("task-card-${ids[0]}")
        val first = node.fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("task-card-${ids[1]}").fetchSemanticsNode().boundsInRoot
        val delta = second.center.y - first.center.y + 12f
        rule.mainClock.autoAdvance = false
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(650)
        node.performTouchInput {
            advanceEventTime(650)
            moveBy(Offset(0f, delta / 2))
        }
        rule.mainClock.advanceTimeByFrame()
        node.performTouchInput { moveBy(Offset(0f, delta / 2)) }
        rule.mainClock.advanceTimeByFrame()
        val releaseTop = rule.onNodeWithTag("drag-overlay").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The held card must follow the finger: original=${first.top}, moved=$releaseTop", releaseTop > first.top + delta / 2)
        return first.top to releaseTop
    }

    @Test fun releaseKeepsCardAtFingerAndCommitsNewOrder() {
        val (originalTop, releaseTop) = dragFirst()
        rule.onNodeWithTag("task-card-${ids[0]}").performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        val afterRelease = rule.onNodeWithTag("drag-overlay").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The card must not jump back: original=$originalTop release=$releaseTop after=$afterRelease", abs(afterRelease - releaseTop) < abs(afterRelease - originalTop))
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5000) { runBlocking { db.taskDao().getAllOnce().map { it.id } } == listOf(ids[1], ids[0], ids[2]) }
        rule.waitForIdle()
        rule.onNodeWithTag("drag-overlay").assertDoesNotExist()
    }

    @Test fun cancelledDragKeepsOriginalOrder() {
        dragFirst()
        rule.onNodeWithTag("task-card-${ids[0]}").performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(ids, runBlocking { db.taskDao().getAllOnce().map { it.id } })
        rule.onNodeWithTag("drag-overlay").assertDoesNotExist()
    }
}
