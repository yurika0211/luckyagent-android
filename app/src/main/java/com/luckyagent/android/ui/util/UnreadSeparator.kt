package com.luckyagent.android.ui.util

sealed interface TimelineRow {
    val key: String
}

data class MessageRow(override val key: String) : TimelineRow
data class UnreadSeparatorRow(override val key: String, val count: Int, val beforeMessageId: String) : TimelineRow

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
