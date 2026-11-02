package com.tasknext.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HubSwipePolicyTest {
    @Test fun shortSlowDragReturnsHome() {
        assertEquals(1, hubSwipeTarget(1.10f, 1, 0f, 2f))
        assertEquals(1, hubSwipeTarget(0.90f, 1, 0f, 2f))
    }

    @Test fun deliberateDragCommitsInEitherDirection() {
        assertEquals(2, hubSwipeTarget(1.30f, 1, 0f, 2f))
        assertEquals(0, hubSwipeTarget(0.70f, 1, 0f, 2f))
    }

    @Test fun quickFlickCommitsWithoutLongTravel() {
        assertEquals(2, hubSwipeTarget(1.08f, 1, 3f, 2f))
        assertEquals(0, hubSwipeTarget(0.92f, 1, -3f, 2f))
    }

    @Test fun reversingFlingReturnsToThePageInsteadOfContinuingAway() {
        assertEquals(1, hubSwipeTarget(1.40f, 1, -3f, 2f))
        assertEquals(1, hubSwipeTarget(0.60f, 1, 3f, 2f))
    }

    @Test fun interruptedSettleCanReverseFromItsVisiblePosition() {
        assertEquals(1, hubSwipeTarget(1.60f, 2, -3f, 2f))
    }

    @Test fun outerEdgesStayInRange() {
        assertEquals(0, hubSwipeTarget(0f, 0, -3f, 2f))
        assertEquals(2, hubSwipeTarget(2f, 2, 3f, 2f))
    }
}
