package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBubbleTest {
    @Test
    fun holdTalkReleaseAndHints() {
        val idle = HoldTalkGesture(0f, 0f, slopPx = 8f, cancelPx = 72f, lockPx = 96f)
        assertEquals(HoldTalkPhase.Idle, holdTalkPhase(idle, locked = false))
        assertTrue(holdTalkTapWasAccidental(HoldTalkPhase.Idle, movedPastSlop = false))
        val cancel = idle.copy(dx = -80f, dy = 4f)
        assertEquals(HoldTalkPhase.CancelArmed, holdTalkPhase(cancel, locked = false))
        assertFalse(holdTalkReleaseSends(HoldTalkPhase.CancelArmed))
        assertTrue(holdTalkReleaseSends(HoldTalkPhase.Holding))
        val locking = idle.copy(dx = -4f, dy = -40f)
        assertEquals(HoldTalkPhase.Locking, holdTalkPhase(locking, locked = false))
        assertEquals(1f, holdTalkLockProgress(-96f, 96f), 0.01f)
        assertEquals(HoldTalkPhase.Locked, holdTalkPhase(idle, locked = true))
        assertEquals(1, nextLockHintCount(0))
        assertEquals(3, nextLockHintCount(2))
        assertEquals(null, nextLockHintCount(3))
        assertTrue(nextPauseHintSeen(false))
        assertFalse(nextPauseHintSeen(true))
    }
}