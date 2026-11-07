package com.luckyagent.android.ui.util

import com.luckyagent.android.ui.AttachmentDownloadPhase
import com.luckyagent.android.ui.AttachmentDownloadUi

/** Display and save name. Drops path tricks, controls, and reserved characters. */
fun sanitizeFileName(raw: String?): String {
    val leaf = raw.orEmpty().replace('\\', '/').substringAfterLast('/').trim()
    val cleaned = buildString(leaf.length) {
        leaf.forEach { ch ->
            if (ch.code < 32 || ch == '\u202E' || ch in "<>:\"/\\|?*") return@forEach
            append(ch)
        }
    }.trim('.', ' ')
    return cleaned.ifEmpty { "文件" }
}

/** Extension including the dot, or empty when the name has none. */
fun fileNameExtension(name: String): String {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || name.length - dot > 8) return ""
    return name.substring(dot)
}

/** Short type label from the extension, otherwise the mime subtype. */
fun fileTypeLabel(name: String, mimeType: String?): String {
    val ext = fileNameExtension(name).removePrefix(".").uppercase()
    if (ext.isNotEmpty()) return ext
    val subtype = mimeType.orEmpty().substringAfter('/', "").substringBefore(';').trim()
    return subtype.uppercase().ifEmpty { "文件" }
}

enum class FileBubbleAction { Download, Cancel, Open, Retry }

/** Circle button for one incoming file. Image, voice, and video stay on their own cards. */
fun fileBubbleAction(download: AttachmentDownloadUi?): FileBubbleAction = when (download?.phase) {
    AttachmentDownloadPhase.Running -> FileBubbleAction.Cancel
    AttachmentDownloadPhase.Done -> FileBubbleAction.Open
    AttachmentDownloadPhase.Failed -> FileBubbleAction.Retry
    else -> FileBubbleAction.Download
}

/**
 * Subtitle under the file name.
 * Idle and done show size plus type. Running shows copied / total, or a percent when that is shorter.
 * A zero total never renders as `0 / 0`.
 */
fun fileBubbleSubtitle(
    bytes: Long?,
    mimeType: String?,
    name: String,
    download: AttachmentDownloadUi?,
    lockedTotal: Long?,
): String {
    val type = fileTypeLabel(name, mimeType)
    val knownSize = bytes?.takeIf { it > 0L }?.let(::formatTransferSize)
    return when (download?.phase) {
        AttachmentDownloadPhase.Running -> {
            val total = lockedTotal?.takeIf { it > 0L } ?: download.bytesTotal?.takeIf { it > 0L }
            val copied = formatTransferSize(download.bytesCopied)
            when {
                total != null && total > 0L -> {
                    val ratio = "$copied / ${formatTransferSize(total)}"
                    val percent = downloadPercent(download.bytesCopied, total)
                    if (percent != null && ratio.length > 22) "$percent%" else ratio
                }
                else -> copied
            }
        }
        AttachmentDownloadPhase.Failed -> download.error?.takeIf { it.isNotBlank() } ?: "下载失败"
        else -> listOfNotNull(knownSize, type).joinToString(" · ").ifEmpty { type }
    }
}

/** First positive total wins so the subtitle width does not jump. */
fun lockDownloadTotal(previous: Long?, reported: Long?): Long? =
    previous?.takeIf { it > 0L } ?: reported?.takeIf { it > 0L }

/** Dialog body when no app can open the file. Includes the mime. */
fun openFailureMessage(mimeType: String?, error: String?): String {
    val mime = mimeType?.trim()?.takeIf { it.isNotEmpty() } ?: "未知类型"
    val detail = error?.trim()?.takeIf { it.isNotEmpty() } ?: "没有可用的查看应用"
    return "打开失败 · $mime · $detail"
}

/** Middle ellipsis that keeps the extension visible. */
fun middleEllipsisFileName(name: String, limit: Int = 28): String {
    val safe = sanitizeFileName(name)
    if (safe.length <= limit) return safe
    val dot = safe.lastIndexOf('.')
    val ext = if (dot > 0 && safe.length - dot <= 8) safe.substring(dot) else ""
    val stemBudget = (limit - ext.length - 1).coerceAtLeast(1)
    val head = stemBudget / 2
    val tail = stemBudget - head
    val stem = safe.removeSuffix(ext)
    return stem.take(head) + "…" + stem.takeLast(tail) + ext
}
