package com.tasknext.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.tasknext.app.data.TaskGroupDefinition
import com.tasknext.app.data.TaskGroups
import com.tasknext.app.data.TaskSize
import com.tasknext.app.ui.theme.TaskNextTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class TaskEditorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun simpleCreationKeepsDefaultsAndOptionalDraftSurvivesRecreation() {
        var savedTitle: String? = null
        var savedNote: String? = null
        var savedSize: TaskSize? = null
        var savedGroup: TaskGroupDefinition? = null
        val restore = StateRestorationTester(rule)
        restore.setContent {
            TaskNextTheme {
                AddEditTaskDialog(null, TaskGroups.builtIns, 9, 0, {}, {}, {},
                    onSave = { title, group, size, note, reminder, hour, minute ->
                        savedTitle = title; savedNote = note; savedSize = size; savedGroup = group
                        assertTrue(reminder); assertNull(hour); assertNull(minute)
                    })
            }
        }
        rule.onNodeWithText("Comment (optional)").assertDoesNotExist()
        rule.onNodeWithText("Title").performTextInput("Read a chapter")
        rule.onNodeWithText("Small").performClick()
        rule.onNodeWithText("More options: group, notes, reminder").performClick()
        rule.onNodeWithText("Comment (optional)").performScrollTo().performTextInput("Start on page 12")
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Start on page 12").assertExists()
        rule.onNodeWithText("Hide options").performScrollTo().performClick()
        rule.onNodeWithText("Add", useUnmergedTree = true).performClick()
        rule.runOnIdle {
            assertEquals("Read a chapter", savedTitle)
            assertEquals("Start on page 12", savedNote)
            assertEquals(TaskSize.SMALL, savedSize)
            assertEquals(TaskGroups.default, savedGroup)
        }
    }
}
