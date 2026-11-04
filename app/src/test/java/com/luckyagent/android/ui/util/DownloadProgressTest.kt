package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    @Test fun speedUsesCopiedBytesOverElapsed() {
        assertEquals(0L, downloadSpeedBytesPerSecond(0L, 1_000L))
        assertEquals(0L, downloadSpeedBytesPerSecond(1024L, 0L))
        assertEquals(1_200L * 1024L, downloadSpeedBytesPerSecond(1_200L * 1024L, 1_000L))
        assertEquals(512L * 1024L, downloadSpeedBytesPerSecond(1024L * 1024L, 2_000L))
    }

    @Test fun labelShowsTotalWhenKnownAndCopiedSizeOtherwise() {
        val speed = 1_200L * 1024L
        assertEquals(
            "2.1 MB / 8.0 MB · 1.2 MB/s",
            downloadProgressLabel((2.1 * 1024 * 1024).toLong(), (8.0 * 1024 * 1024).toLong(), speed),
        )
        assertEquals(
            "2.1 MB · 1.2 MB/s",
            downloadProgressLabel((2.1 * 1024 * 1024).toLong(), null, speed),
        )
    }
}
