package com.tasknext.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskInteractionTest {
    @Test fun fridayMarksTomorrowAndDayAfterAsWeekend() {
        val friday = LocalDate.of(2026, 10, 23)
        assertTrue(isWeekendQueueDate(friday, 0))
        assertTrue(isWeekendQueueDate(friday, 1))
        assertFalse(isWeekendQueueDate(friday, 2))
    }
    @Test fun laterCalendarDateUsesTheSameWeekendRule() {
        val monday = LocalDate.of(2026, 10, 26)
        assertTrue(isWeekendQueueDate(monday, 4))
        assertTrue(isWeekendQueueDate(monday, 5))
        assertFalse(isWeekendQueueDate(monday, 6))
    }
    @Test fun insertingBetweenRowsRetainsIndicesOutsideViewport() {
        val visible = listOf(8 to 100f, 9 to 200f, 10 to 300f)
        assertEquals(8, insertionIndexAt(90f, visible, 0))
        assertEquals(9, insertionIndexAt(150f, visible, 0))
        assertEquals(11, insertionIndexAt(350f, visible, 0))
    }
    @Test fun previewMovesOnlyRowsBetweenOriginalAndDestination() {
        assertEquals(-80f, previewDisplacement(1, 3, 2, 80f), 0f)
        assertEquals(-80f, previewDisplacement(1, 3, 3, 80f), 0f)
        assertEquals(0f, previewDisplacement(1, 3, 4, 80f), 0f)
        assertEquals(80f, previewDisplacement(3, 1, 1, 80f), 0f)
    }
    @Test fun draggingFromActiveSectionOpensSpaceWithoutAnOriginalPoolSlot() {
        assertEquals(0f, previewDisplacement(-1, 2, 1, 80f), 0f)
        assertEquals(80f, previewDisplacement(-1, 2, 2, 80f), 0f)
    }
}
