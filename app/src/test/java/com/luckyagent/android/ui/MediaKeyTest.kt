package com.luckyagent.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKeyTest {
    @Test
    fun opaqueUriDoesNotThrowWhenCanonicalized() {
        assertEquals("artifact:workspace/report.png", canonicalMediaKey("artifact:workspace/report.png"))
    }

    @Test
    fun localArtifactPathIsCanonicalized() {
        assertEquals(
            "artifact:uploads/report.png",
            canonicalMediaKey("/home/user/.luckyagent/uploads/report.png"),
        )
    }
}
