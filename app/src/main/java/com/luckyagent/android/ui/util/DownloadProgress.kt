package com.luckyagent.android.ui.util

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

/** `2.1 MB / 8.0 MB · 1.2 MB/s`, or copied size plus speed when length is unknown. */
internal fun downloadProgressLabel(bytesCopied: Long, bytesTotal: Long?, bytesPerSecond: Long): String {
    val copied = formatTransferSize(bytesCopied)
    val speed = formatTransferSize(bytesPerSecond) + "/s"
    val total = bytesTotal?.takeIf { it > 0L } ?: return "$copied · $speed"
    return "$copied / ${formatTransferSize(total)} · $speed"
}
