package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGrowTest {
    @Test
    fun dragSkipsTheHeightTransition() {
        assertEquals(80, bubbleDrawHeight(40, 80, 0.5f, dragging = true))
        assertEquals(60, bubbleDrawHeight(40, 80, 0.5f, dragging = false))
        assertTrue(bubbleTransitionDone(1f))
    }
}
