package com.shilapi.xcertplay

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MotionEvent.actionToString` is API 19 and this value is read on every touch forwarded to
 * CarPlay, so it has to be rendered without it on Android 4.2/4.3. Robolectric cannot run those
 * releases, so the platform comparison runs at the oldest SDK that has the method.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class TouchActionNameTest {
    @Test fun everyActionTheHostActsOnIsNamed() {
        assertEquals("ACTION_DOWN", touchActionName(MotionEvent.ACTION_DOWN))
        assertEquals("ACTION_UP", touchActionName(MotionEvent.ACTION_UP))
        assertEquals("ACTION_MOVE", touchActionName(MotionEvent.ACTION_MOVE))
        assertEquals("ACTION_CANCEL", touchActionName(MotionEvent.ACTION_CANCEL))
        assertEquals("ACTION_OUTSIDE", touchActionName(MotionEvent.ACTION_OUTSIDE))
        assertEquals("ACTION_POINTER_DOWN(0)", touchActionName(MotionEvent.ACTION_POINTER_DOWN))
        assertEquals("ACTION_POINTER_UP(0)", touchActionName(MotionEvent.ACTION_POINTER_UP))
    }

    /** An unmapped action still has to be recognisable in a report rather than silently blank. */
    @Test fun anUnknownActionKeepsItsNumericValue() {
        assertEquals("action:99", touchActionName(99))
        assertEquals("action:-1", touchActionName(-1))
    }

    @Test fun theNamesMatchWhatThePlatformWouldHaveSaid() {
        val known = listOf(
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE,
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP,
        )
        for (action in known) {
            assertEquals(
                "action $action",
                MotionEvent.actionToString(action),
                touchActionName(action),
            )
        }
    }
}