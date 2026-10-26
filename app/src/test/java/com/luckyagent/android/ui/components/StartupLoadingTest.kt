package com.luckyagent.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLoadingTest {
    @Test fun reducedMotionStopsTheClover() {
        assertTrue(startupMotionReduced(0f))
        assertFalse(startupMotionReduced(1f))
        assertFalse(startupMotionReduced(0.5f))
    }

    @Test fun timeoutReplacesThePhaseInsteadOfStackingAnotherSentence() {
        assertEquals(StartupPhase.Sessions, startupSpokenPhase(StartupPhase.Sessions, timedOut = false))
        assertEquals("这轮对话还没读到。可以重试。", startupSpokenPhase(StartupPhase.History, timedOut = true))
        assertEquals("还没连上。可以重试、改地址或重新扫码。", startupSpokenPhase(StartupPhase.Connecting, timedOut = true))
        assertFalse(startupSpokenPhase(StartupPhase.History, timedOut = true).contains("还没连上"))
    }

    @Test fun coldStartNamesTheStepInsteadOfAGenericSpinner() {
        assertEquals("正在连接…", StartupPhase.Connecting)
        assertEquals("正在读取会话", StartupPhase.Sessions)
        assertEquals("正在读取这轮对话", StartupPhase.History)
        assertTrue(StartupPhase.SessionsNext.contains("上次的会话"))
        assertTrue(StartupPhase.HistoryNext.contains("上次的对话"))
        assertEquals(8_000L, StartupWaitLimitMs)
    }
}
