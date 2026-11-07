package com.luckyagent.android.ui.util

sealed interface TimelineRow {
    val key: String
}

data class MessageRow(override val key: String) : TimelineRow
data class UnreadSeparatorRow(override val key: String, val count: Int, val beforeMessageId: String) : TimelineRow
data class DateSeparatorRow(override val key: String, val label: String) : TimelineRow

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
