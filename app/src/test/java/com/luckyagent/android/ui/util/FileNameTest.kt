package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameTest {
    @Test
    fun sanitizeStripsPathAndReservedCharacters() {
        assertEquals("文件", sanitizeFileName("   "))
        assertEquals("文件", sanitizeFileName("../"))
        assertEquals("report.pdf", sanitizeFileName("../report.pdf"))
        assertEquals("ab.pdf", sanitizeFileName("a\u202Eb<>:\"|?*.pdf"))
    }

    @Test
    fun middleEllipsisKeepsTheExtension() {
        val shown = middleEllipsisFileName("very-long-report-name.pdf", 18)
        assertTrue(shown.endsWith(".pdf"))
        assertTrue(shown.contains("…"))
        assertTrue(shown.length <= 18)
    }

    @Test
    fun bubbleSubtitleAndOpenFailureStayOnTheFile() {
        assertEquals("12.4 MB · PDF", fileBubbleSubtitle(13_000_000L, "application/pdf", "report.pdf", null, null))
        val running = com.luckyagent.android.ui.AttachmentDownloadUi(
            phase = com.luckyagent.android.ui.AttachmentDownloadPhase.Running,
            bytesCopied = 4_400_000L,
            bytesTotal = 13_000_000L,
        )
        val line = fileBubbleSubtitle(13_000_000L, "application/pdf", "report.pdf", running, 13_000_000L)
        assertTrue(line.contains("/"))
        assertTrue(!line.contains("0 / 0"))
        assertEquals(FileBubbleAction.Cancel, fileBubbleAction(running))
        assertEquals(FileBubbleAction.Open, fileBubbleAction(running.copy(phase = com.luckyagent.android.ui.AttachmentDownloadPhase.Done)))
        assertEquals(13_000_000L, lockDownloadTotal(13_000_000L, 99L))
        val failure = openFailureMessage("application/pdf", "没有可用的查看应用")
        assertTrue(failure.contains("application/pdf"))
    }
}
