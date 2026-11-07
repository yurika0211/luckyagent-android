package com.luckyagent.android.ui.util

sealed interface TimelineRow {
    val key: String
}

data class MessageRow(override val key: String) : TimelineRow
data class UnreadSeparatorRow(override val key: String, val count: Int, val beforeMessageId: String) : TimelineRow
data class DateSeparatorRow(override val key: String, val label: String) : TimelineRow

/**
 * Id of the message at or just below a painted row. Date and unread separators
 * are not messages, so a scroll anchor stored from the first visible row has to
 * step past them or it will never match a message id.
 */
/**
 * Rows actually painted: one date row above the first message of each day,
 * then the unread row from [withUnreadSeparator]. Scroll math has to count
 * these rows, because the message list alone is shorter.
 */
fun paintedChatRows(
    messageIds: List<String>,
    days: List<java.time.LocalDate?>,
    readMessageId: String?,
): List<TimelineRow> {
    val base = withUnreadSeparator(messageIds, readMessageId)
    val marks = dateRows(messageIds, days)
    return buildList {
        base.forEach { row ->
            marks.firstOrNull { it.beforeMessageId == row.key }?.let { mark ->
                add(DateSeparatorRow(mark.key, mark.label))
            }
            add(row)
        }
    }
}

fun firstMessageIdAt(rows: List<TimelineRow>, firstVisibleIndex: Int): String {
    if (rows.isEmpty()) return ""
    val start = firstVisibleIndex.coerceIn(0, rows.lastIndex)
    for (index in start until rows.size) {
        val row = rows[index]
        if (row is MessageRow) return row.key
    }
    return ""
}

/** First tap lands on the unread row. The next tap, or a tap with no unread, lands on the tail. */
fun jumpTargetIndex(rows: List<TimelineRow>, seenUnread: Boolean): Int {
    if (rows.isEmpty()) return 0
    val unread = rows.indexOfFirst { it is UnreadSeparatorRow }
    if (unread >= 0 && !seenUnread) return unread
    return rows.lastIndex
}

/** Insert one unread row above the first unread message. None when the count is 0. */
fun withUnreadSeparator(messageIds: List<String>, readMessageId: String?): List<TimelineRow> {
    val index = firstUnreadIndex(messageIds, readMessageId)
    if (index < 0) return messageIds.map { MessageRow(it) }
    val count = messageIds.size - index
    val before = messageIds[index]
    return buildList {
        messageIds.forEachIndexed { i, id ->
            if (i == index) add(UnreadSeparatorRow(key = "unread-$before", count = count, beforeMessageId = before))
            add(MessageRow(id))
        }
    }
}
