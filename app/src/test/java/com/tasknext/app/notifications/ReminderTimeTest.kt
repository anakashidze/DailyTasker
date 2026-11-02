package com.tasknext.app.notifications

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ReminderTimeTest {
    private val now = LocalDateTime.of(2026, 10, 24, 10, 30)
    @Test fun futureReminderStaysOnToday() {
        assertEquals(now.withHour(14).withMinute(0), NotificationScheduler.taskReminderTime(14, 0, now))
    }
    @Test fun pastTimeDoesNotSilentlyMoveToTomorrow() {
        assertNull(NotificationScheduler.taskReminderTime(9, 0, now))
    }
    @Test fun elapsedMinuteDoesNotImmediatelyRearmTheSameAlarm() {
        assertNull(NotificationScheduler.taskReminderTime(10, 30, now))
    }
}
