package com.luckyagent.android.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class PerfFrameTrackerTest {
    @Test
    fun aggregatesFrameTimingAndDroppedFrames() {
        val probe = PerfProbe(enabled = true)
        val tracker = PerfFrameTracker(
            section = "scroll.chat",
            probe = probe,
            extraAttributes = mapOf("pager" to "chat"),
        )

        tracker.begin(1_000_000L)
        tracker.frame(17_000_000L)
        tracker.frame(37_000_000L)
        tracker.end(47_000_000L)

        val sample = probe.snapshot().single()
        assertEquals("chat", sample.attributes["pager"])
        assertEquals("3", sample.attributes["frame_count"])
        assertEquals("1", sample.attributes["dropped_frames"])
        assertEquals(3, sample.inputUnits)
    }
}
