package com.luckyagent.android.ui.util

import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AttachmentDownloadPhase
import com.luckyagent.android.ui.AttachmentDownloadUi
import com.luckyagent.android.ui.AttachmentNotice

import java.util.Locale

/** Bytes transferred over [elapsedMs], or 0 when either side has not started. */
internal fun downloadSpeedBytesPerSecond(bytesCopied: Long, elapsedMs: Long): Long {
    if (bytesCopied <= 0L || elapsedMs <= 0L) return 0L
    return bytesCopied * 1000L / elapsedMs
}

internal fun formatTransferSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    return when {
        safe >= 1024.0 * 1024.0 -> "%.1f MB".format(Locale.US, safe / (1024.0 * 1024.0))
        safe >= 1024.0 -> "%.1f KB".format(Locale.US, safe / 1024.0)
        else -> "${bytes.coerceAtLeast(0L)} B"
    }
}

/** Whole percent when [bytesTotal] is known, otherwise null so the bar stays indeterminate. */
internal fun downloadPercent(bytesCopied: Long, bytesTotal: Long?): Int? {
    val total = bytesTotal?.takeIf { it > 0L } ?: return null
    return ((bytesCopied.coerceAtLeast(0L) * 100L) / total).toInt().coerceIn(0, 100)
}

/** Complete or failed download toast. Running stays on the card and the progress row. */
internal fun attachmentTerminalNotice(
    text: String,
    isError: Boolean = false,
    isComplete: Boolean = false,
    openUri: String? = null,
    mimeType: String? = null,
): AttachmentNotice = AttachmentNotice(
    text = text,
    isError = isError,
    isComplete = isComplete,
    openUri = openUri,
    mimeType = mimeType,
)

/** Starts a download without writing the activity line or a bottom notice. */
internal fun attachmentRunningUpdate(state: AppUiState, key: String): AppUiState {
    val running = AttachmentDownloadUi(phase = AttachmentDownloadPhase.Running)
    return state.copy(
        attachmentDownloadKey = key,
        attachmentDownload = running,
        attachmentDownloads = state.attachmentDownloads + (key to running),
        attachmentNotice = null,
    )
}

/** Composer caption while a download is running. Never the filename sentence. */
internal fun composerDownloadCaption(download: AttachmentDownloadUi): String? {
    if (download.phase != AttachmentDownloadPhase.Running) return null
    val label = download.label
    if (download.bytesCopied <= 0L && download.bytesPerSecond <= 0L && download.bytesTotal == null) return null
    return label
}

/** `26% · 2.1 MB / 8.0 MB · 1.2 MB/s`, or copied size plus speed when length is unknown. */
internal fun downloadProgressLabel(bytesCopied: Long, bytesTotal: Long?, bytesPerSecond: Long): String {
    val copied = formatTransferSize(bytesCopied)
    val speed = formatTransferSize(bytesPerSecond) + "/s"
    val total = bytesTotal?.takeIf { it > 0L } ?: return "$copied · $speed"
    val percent = downloadPercent(bytesCopied, total) ?: return "$copied / ${formatTransferSize(total)} · $speed"
    return "$percent% · $copied / ${formatTransferSize(total)} · $speed"
}
