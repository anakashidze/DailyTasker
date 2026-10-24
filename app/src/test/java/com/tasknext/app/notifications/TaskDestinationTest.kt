package com.tasknext.app.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.tasknext.app.MainActivity
import com.tasknext.app.TaskDestination
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaskDestinationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Test fun taskNotificationsKeepDistinctDestinationsEvenWithSameRequestCode() {
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        ReminderReceiver().showNotification(context, 15L, "First", 10)
        val first = manager.getNotification(15).contentIntent
        val otherId = 15L + (1L shl 32)
        ReminderReceiver().showNotification(context, otherId, "Second", 10)
        val second = manager.getNotification(15).contentIntent
        assertNotEquals(first, second)
        assertEquals(15L, TaskDestination.request(shadowOf(first).savedIntent)?.taskId)
        assertEquals(otherId, TaskDestination.request(shadowOf(second).savedIntent)?.taskId)
    }
    @Test fun widgetAndPlanningDestinationOpensTodayWithoutReopeningAnOldTask() {
        val today = TaskDestination.intent(context)
        assertNotNull(TaskDestination.request(today))
        assertNull(TaskDestination.request(today)?.taskId)
        assertTrue(today.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertNull(TaskDestination.request(Intent(context, MainActivity::class.java)))
    }
}
