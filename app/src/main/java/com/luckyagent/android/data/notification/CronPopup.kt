package com.luckyagent.android.data.notification

import com.luckyagent.android.data.api.CronJob
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * A cron reminder the phone should show. The computer still owns the schedule;
 * this only decides whether the phone has something new to surface.
 */
data class CronPopup(
    val jobId: String,
    val sessionId: String,
    val title: String,
    val body: String,
    val eventId: String,
)

/** Delay before the next background check. Not a poll interval. */
fun cronWatchDelayMs(nextRun: String?, nowMs: Long, fallbackMs: Long = CRON_WATCH_FALLBACK_MS): Long {
    val due = parseCronInstant(nextRun)?.toEpochMilli() ?: return fallbackMs
    val until = due - nowMs
    return when {
        until <= 0L -> CRON_WATCH_GRACE_MS
        until < CRON_WATCH_GRACE_MS -> CRON_WATCH_GRACE_MS
        else -> (until + CRON_WATCH_GRACE_MS).coerceAtMost(fallbackMs)
    }
}

/**
 * A run counts as new only after the phone has already seen this job once.
 * The first observation records the fingerprint and stays quiet.
 */
fun cronPopupForChange(job: CronJob, previousFingerprint: String?): CronPopup? {
    if (previousFingerprint == null) return null
    val fingerprint = cronFingerprint(job)
    if (fingerprint == previousFingerprint) return null
    if (job.lastRun.isNullOrBlank() && job.runCount <= 0 && job.lastError.isNullOrBlank()) return null
    val name = job.name.ifBlank { job.id }
    val detail = job.lastError?.takeIf { it.isNotBlank() } ?: "已运行 ${job.runCount} 次"
    val sessionId = job.metadata["session_id"].orEmpty()
    return CronPopup(
        jobId = job.id,
        sessionId = sessionId,
        title = name,
        body = "$name · $detail",
        eventId = "${job.id}:${job.lastRun}:${job.runCount}",
    )
}

fun cronFingerprint(job: CronJob): String =
    listOf(job.lastRun, job.runCount, job.errorCount, job.status, job.lastError).joinToString("|")

/** The person is already looking at the session this run wrote into. */
fun cronPopupSuppressed(appInForeground: Boolean, destinationIsChat: Boolean, currentSessionId: String, popupSessionId: String): Boolean =
    appInForeground && destinationIsChat && popupSessionId.isNotBlank() && popupSessionId == currentSessionId

fun parseCronInstant(value: String?): Instant? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }
}

const val CRON_WATCH_FALLBACK_MS = 15 * 60 * 1000L
const val CRON_WATCH_GRACE_MS = 20_000L
