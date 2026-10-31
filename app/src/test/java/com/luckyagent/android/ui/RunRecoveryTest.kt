package com.luckyagent.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRecoveryTest {
    @Test
    fun knownRequestIdWinsOverFallback() {
        assertEquals(
            "run-2",
            selectTerminalRunId("run-2", listOf("run-1", "run-2"), "run-1"),
        )
    }

    @Test
    fun oneActiveRunCanBeClosedWhenTerminalFrameOmitsRequestId() {
        assertEquals("run-1", selectTerminalRunId(null, listOf("run-1"), null))
    }

    @Test
    fun severalRunsUseTheForegroundRunAsTheSafeFallback() {
        assertEquals(
            "foreground",
            selectTerminalRunId("unknown", listOf("background", "foreground"), "foreground"),
        )
    }

    @Test
    fun ambiguousRunsWithoutForegroundAreLeftUnmatched() {
        assertNull(selectTerminalRunId(null, listOf("run-1", "run-2"), null))
    }

    @Test
    fun visibleUiSettlesEvenAfterTheRunWasReplayedAway() {
        assertTrue(terminalFrameShouldSettle(hasVisibleTurn = true, selectedRunId = null))
    }

    @Test
    fun idleRunsAreSelectedByTimeout() {
        assertEquals(
            setOf("old"),
            staleRunIds(
                lastEventAt = mapOf("old" to 1_000L, "fresh" to 59_500L),
                now = 61_000L,
                timeoutMs = 60_000L,
            ),
        )
    }
}
