package com.luckyagent.android.ui.util

import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AttachmentDownloadPhase
import com.luckyagent.android.ui.AttachmentDownloadUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val copied = (2.1 * 1024 * 1024).toLong()
        val total = (8.0 * 1024 * 1024).toLong()
        assertEquals(26, downloadPercent(copied, total))
        assertEquals(null, downloadPercent(copied, null))
        assertEquals(null, downloadPercent(copied, 0L))
        assertEquals(
            "26% · 2.1 MB / 8.0 MB · 1.2 MB/s",
            downloadProgressLabel(copied, total, speed),
        )
        assertEquals(
            "2.1 MB · 1.2 MB/s",
            downloadProgressLabel((2.1 * 1024 * 1024).toLong(), null, speed),
        )
    }

    @Test fun runningDownloadDoesNotPublishFilenameLines() {
        val started = attachmentRunningUpdate(
            AppUiState(activityLine = "正在回复"),
            "apk",
        )
        assertNull(started.attachmentNotice)
        assertEquals("正在回复", started.activityLine)
        assertFalse(started.activityLine.orEmpty().startsWith("下载中 ·"))
        assertFalse(started.activityLine.orEmpty().startsWith("准备下载 ·"))
        assertNull(composerDownloadCaption(started.attachmentDownload))
        val caption = composerDownloadCaption(
            started.attachmentDownload.copy(bytesCopied = (2.1 * 1024 * 1024).toLong(), bytesTotal = (8.0 * 1024 * 1024).toLong(), bytesPerSecond = 1_200L * 1024L),
        )
        assertEquals("26% · 2.1 MB / 8.0 MB · 1.2 MB/s", caption)
        assertFalse(caption.orEmpty().contains("下载中 ·"))
    }

    @Test fun terminalNoticeDoesNotBecomeTheActivityLine() {
        val complete = attachmentTerminalNotice("下载完成 · app.apk · Downloads/app.apk", isComplete = true)
        assertTrue(complete.text.startsWith("下载完成 ·"))
        assertTrue(complete.isComplete)
        val failed = attachmentTerminalNotice("下载失败 · 网络中断", isError = true)
        assertTrue(failed.text.startsWith("下载失败 ·"))
        val missing = attachmentTerminalNotice("附件没有可下载的地址", isError = true)
        assertEquals("附件没有可下载的地址", missing.text)
        val state = AppUiState(activityLine = "正在回复", attachmentNotice = complete)
        assertFalse(state.activityLine.orEmpty().startsWith("下载完成 ·"))
        val composer = listOfNotNull(
            state.activityLine,
            composerDownloadCaption(AttachmentDownloadUi(phase = AttachmentDownloadPhase.Running, bytesCopied = 1, bytesTotal = 4)),
            state.attachmentNotice?.takeIf { it.isComplete || it.isError }?.text,
        ).joinToString("\n")
        val runningOnly = composerDownloadCaption(
            AttachmentDownloadUi(phase = AttachmentDownloadPhase.Running, bytesCopied = 1, bytesTotal = 4, bytesPerSecond = 10),
        ).orEmpty()
        assertEquals(0, runningOnly.split("下载中 ·").size - 1)
        assertTrue(composer.contains("下载完成 ·"))
    }
}
