package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGrowTest {
    @Test
    fun appendKeepsEarlierPieces() {
        val before = bubblePieces("第一段\n\n第二段")
        val after = bubblePieces("第一段\n\n第二段更多")
        assertEquals(before.first(), after.first())
        assertTrue(pieceHash(before.first()) == pieceHash(after.first()))
    }

    @Test
    fun dragSkipsTheHeightTransition() {
        assertEquals(80, bubbleDrawHeight(40, 80, 0.5f, dragging = true))
        assertEquals(60, bubbleDrawHeight(40, 80, 0.5f, dragging = false))
        assertTrue(bubbleTransitionDone(1f))
    }
}