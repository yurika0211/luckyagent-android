package com.luckyagent.android.data.notification

import com.luckyagent.android.data.api.CronJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronPopupTest {
    @Test fun firstSightingDoesNotPopup() {
        assertNull(cronPopupForChange(ran("a"), previousFingerprint = null))
    }

    @Test fun unchangedRunDoesNotPopup() {
        val job = ran("a")
        assertNull(cronPopupForChange(job, cronFingerprint(job)))
    }

    @Test fun aNewRunNamesTheJobAndTheCount() {
        val before = ran("morning", runCount = 1)
        val after = ran("morning", runCount = 2, lastRun = "2026-10-25T01:00:00Z")
        val popup = cronPopupForChange(after, cronFingerprint(before))
        assertEquals("morning", popup?.title)
        assertEquals("morning · 已运行 2 次", popup?.body)
        assertEquals("chat-1", popup?.sessionId)
        assertTrue(popup?.eventId.orEmpty().contains("2026-10-25T01:00:00Z"))
    }

    @Test fun aFailureUsesTheErrorInsteadOfTheCount() {
        val before = ran("morning")
        val after = before.copy(errorCount = 1, lastError = "电脑关机了")
        assertEquals("morning · 电脑关机了", cronPopupForChange(after, cronFingerprint(before))?.body)
    }

    @Test fun watchingTheSameSessionSuppressesThePopup() {
        assertTrue(cronPopupSuppressed(true, destinationIsChat = true, "chat-1", "chat-1"))
        assertEquals(false, cronPopupSuppressed(true, destinationIsChat = false, "chat-1", "chat-1"))
        assertEquals(false, cronPopupSuppressed(false, destinationIsChat = true, "chat-1", "chat-1"))
        assertEquals(false, cronPopupSuppressed(true, destinationIsChat = true, "chat-1", ""))
    }

    @Test fun delayAimsAtTheNextRunInsteadOfPolling() {
        val now = 1_000_000L
        val soon = "1970-01-01T00:16:50Z" // 1_010_000 ms
        assertEquals(CRON_WATCH_GRACE_MS, cronWatchDelayMs(soon, now))
        assertEquals(CRON_WATCH_GRACE_MS, cronWatchDelayMs("1970-01-01T00:00:00Z", now))
        assertEquals(CRON_WATCH_FALLBACK_MS, cronWatchDelayMs(null, now))
        assertEquals(60_000L + CRON_WATCH_GRACE_MS, cronWatchDelayMs("1970-01-01T00:17:40Z", now))
    }

    private fun ran(id: String, runCount: Int = 1, lastRun: String = "2026-10-25T00:00:00Z") = CronJob(
        id = id,
        name = id,
        lastRun = lastRun,
        runCount = runCount,
        metadata = mapOf("session_id" to "chat-1"),
    )
}
