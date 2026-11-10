package com.luckyagent.android.ui.util

import com.luckyagent.android.data.api.MediaAttachment
import kotlinx.serialization.Serializable

/** Composer text, quote, and unsent files for one session. Empty means no draft. */
@Serializable
data class SessionDraft(
    val sessionId: String,
    val text: String = "",
    val quoteMessageId: String = "",
    val quoteRole: String = "",
    val quoteContent: String = "",
    val media: List<DraftMedia> = emptyList(),
    val savedAtMs: Long = 0L,
) {
    fun isEmpty(): Boolean =
        text.isBlank() && quoteMessageId.isBlank() && media.isEmpty()
}

@Serializable
data class DraftMedia(
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val fileUrl: String? = null,
    val filePath: String? = null,
    val fileId: String? = null,
    val fileSize: Long? = null,
    /** Ordinary-image compression is skipped for this pending pick. Default off. */
    val sendOriginal: Boolean = false,
)

/** Last message the reader has seen in this session. */
data class ReadCursor(
    val sessionId: String,
    val messageId: String,
)

@Serializable
data class ScrollAnchor(
    val messageId: String = "",
    val index: Int = 0,
    val offset: Int = 0,
)

fun draftPreview(draft: SessionDraft?): String =
    draft?.text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        ?: draft?.media?.firstOrNull()?.fileName?.takeIf { it.isNotBlank() }
        ?: ""

/** List row uses the draft when it is newer than the stored recap. */
fun sessionListSummary(recap: String?, draft: SessionDraft?, draftNewer: Boolean): String {
    val preview = draftPreview(draft)
    if (draftNewer && preview.isNotEmpty()) return preview
    return recap.orEmpty().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}

fun unreadAfter(messageIds: List<String>, readMessageId: String?): Int {
    if (messageIds.isEmpty()) return 0
    if (readMessageId.isNullOrBlank()) return messageIds.size
    val index = messageIds.indexOf(readMessageId)
    if (index < 0) return messageIds.size
    return (messageIds.size - index - 1).coerceAtLeast(0)
}

/** Index of the first message after the cursor. -1 when everything is read. */
fun firstUnreadIndex(messageIds: List<String>, readMessageId: String?): Int {
    val count = unreadAfter(messageIds, readMessageId)
    if (count <= 0) return -1
    return messageIds.size - count
}

/**
 * Restore by message id. The stored index is only a fallback when that id
 * is no longer in the list.
 */
fun restoreScrollIndex(
    messageIds: List<String>,
    anchor: ScrollAnchor?,
    lastIndex: Int,
): Pair<Int, Int> {
    if (lastIndex < 0 || messageIds.isEmpty()) return 0 to 0
    val id = anchor?.messageId.orEmpty()
    if (id.isNotEmpty()) {
        val found = messageIds.indexOf(id)
        if (found >= 0) return found.coerceIn(0, lastIndex) to (anchor?.offset ?: 0).coerceAtLeast(0)
    }
    val index = anchor?.index ?: return lastIndex to 0
    if (index <= 0) return lastIndex to 0
    return index.coerceIn(0, lastIndex) to (anchor.offset).coerceAtLeast(0)
}

fun advanceReadCursor(current: String?, lastVisibleId: String?, atBottom: Boolean, messageIds: List<String>): String? {
    if (!atBottom) return current
    val tail = messageIds.lastOrNull() ?: return current
    val visible = lastVisibleId?.takeIf { it.isNotEmpty() } ?: tail
    val currentIndex = current?.let { messageIds.indexOf(it) } ?: -1
    val nextIndex = messageIds.indexOf(visible).takeIf { it >= 0 } ?: messageIds.lastIndex
    if (nextIndex >= currentIndex) return messageIds[nextIndex]
    return current
}

fun draftMediaFrom(
    uri: String,
    fileName: String,
    mimeType: String,
    descriptor: MediaAttachment?,
    sendOriginal: Boolean = false,
): DraftMedia =
    DraftMedia(
        uri = uri,
        fileName = fileName,
        mimeType = mimeType,
        fileUrl = descriptor?.fileUrl,
        filePath = descriptor?.filePath,
        fileId = descriptor?.fileId,
        fileSize = descriptor?.fileSize,
        sendOriginal = sendOriginal,
    )
