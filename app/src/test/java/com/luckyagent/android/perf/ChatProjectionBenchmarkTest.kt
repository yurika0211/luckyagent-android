package com.luckyagent.android.perf

import com.luckyagent.android.ui.toHistoryBubbles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProjectionBenchmarkTest {
    @Test
    fun bothArmsJoinTheSameCalls() {
        val history = ChatProjectionBenchmark.syntheticHistory(40)
        val bubbles = history.flatMapIndexed { index, message -> message.toHistoryBubbles(index) }

        assertEquals(40, ChatProjectionBenchmark.joinByScan(bubbles))
        assertEquals(40, ChatProjectionBenchmark.joinByIndex(bubbles))
    }

    @Test
    fun indexedJoinBeatsLinearScanOnALongHistory() {
        val comparison = ChatProjectionBenchmark.compare(
            ChatProjectionBenchmark.syntheticHistory(800),
            runs = 3,
        )

        assertTrue(
            "indexed join should beat a full scan, speedup=${comparison.speedup}",
            comparison.treatmentWins,
        )
        assertTrue(comparison.speedup > 1.5)
        assertEquals("history.join", comparison.section)
    }
}
