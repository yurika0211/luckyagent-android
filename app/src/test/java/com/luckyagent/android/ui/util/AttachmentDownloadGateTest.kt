package com.luckyagent.android.ui.util

import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.ui.ChatMedia
import com.luckyagent.android.ui.screens.canDownload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentDownloadGateTest {
    private val apiBase = "https://example.test"

    @Test fun httpAndHttpsFileUrlsCanDownload() {
        assertTrue(canDownload(media(fileUrl = "https://example.test/files/a.png"), apiBase))
        assertTrue(canDownload(media(fileUrl = "http://example.test/files/a.png"), apiBase))
        assertTrue(canDownload(media(fileUrl = "  https://cdn.example/a.bin  "), ""))
    }

    @Test fun relativeFileUrlNeedsApiBase() {
        assertTrue(canDownload(media(fileUrl = "api/v1/uploads/report.pdf"), apiBase))
        assertTrue(canDownload(media(fileUrl = "/api/v1/uploads/report.pdf"), apiBase))
        assertFalse(canDownload(media(fileUrl = "api/v1/uploads/report.pdf"), ""))
        assertFalse(canDownload(media(fileUrl = "api/v1/uploads/report.pdf"), "   "))
    }

    @Test fun nonHttpSchemesAndBlankUrlCannotDownload() {
        assertFalse(canDownload(media(fileUrl = "ftp://files.example/a.bin"), apiBase))
        assertFalse(canDownload(media(fileUrl = "file:///tmp/a.bin"), apiBase))
        assertFalse(canDownload(media(fileUrl = "content://media/external/file/1"), apiBase))
        assertFalse(canDownload(media(fileUrl = "   "), apiBase))
        assertFalse(canDownload(media(fileUrl = ""), apiBase))
    }

    @Test fun luckyAgentPathsWithApiBaseCanDownload() {
        assertTrue(canDownload(media(filePath = "~/.luckyagent/workspace/报告 1.html"), apiBase))
        assertTrue(canDownload(media(filePath = "~/.luckyagent/uploads/photo.png"), apiBase))
        assertFalse(canDownload(media(filePath = "~/.luckyagent/workspace/报告 1.html"), ""))
    }

    @Test fun absoluteAndTildePathsWithApiBaseCanDownload() {
        assertTrue(canDownload(media(filePath = "/srv/luckyagent/build/app-debug.apk"), apiBase))
        assertTrue(canDownload(media(filePath = "~/Downloads/report.pdf"), apiBase))
    }

    @Test fun rejectedArtifactPathsCannotDownload() {
        assertFalse(canDownload(media(filePath = "~/.luckyagent/workspace/../uploads/secret"), apiBase))
        assertFalse(canDownload(media(filePath = "~/.luckyagent/workspace/"), apiBase))
        assertFalse(canDownload(media(filePath = "not-an-artifact"), apiBase))
    }

    @Test fun missingUrlAndPathCannotDownload() {
        assertFalse(canDownload(media(), apiBase))
        assertFalse(canDownload(media(fileUrl = null, filePath = null), apiBase))
        assertFalse(canDownload(media(fileUrl = " ", filePath = " "), apiBase))
    }

    private fun media(fileUrl: String? = null, filePath: String? = null) = ChatMedia(
        descriptor = MediaAttachment(fileUrl = fileUrl, filePath = filePath, fileName = "file.bin"),
    )
}
