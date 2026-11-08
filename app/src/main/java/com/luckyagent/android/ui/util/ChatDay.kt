package com.luckyagent.android.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun messageDay(createdAt: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    if (createdAt.isNullOrBlank()) return null
    return runCatching { Instant.parse(createdAt).atZone(zone).toLocalDate() }.getOrNull()
}

fun floatingDateLabel(day: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (day == null) return ""
    return when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> day.toString()
    }
}

data class DayRow(val key: String, val label: String, val beforeMessageId: String)

/** A date row above the first message of a new day. Same-day neighbors get none. */
fun dateRows(ids: List<String>, days: List<LocalDate?>, today: LocalDate = LocalDate.now()): List<DayRow> {
    return buildList {
        ids.forEachIndexed { index, id ->
            val day = days.getOrNull(index) ?: return@forEachIndexed
            val previous = days.getOrNull(index - 1)
            if (previous != day) add(DayRow("day-$day-$id", floatingDateLabel(day, today), id))
        }
    }
}

/**
 * Day of the first visible row. A date or unread separator uses the next message,
 * because those rows are not themselves in the day list.
 */
fun visibleMessageDay(rows: List<TimelineRow>, firstVisibleIndex: Int, dayByMessageId: Map<String, LocalDate?>): LocalDate? {
    if (rows.isEmpty()) return null
    val start = firstVisibleIndex.coerceIn(0, rows.lastIndex)
    for (index in start until rows.size) {
        val row = rows[index]
        if (row is MessageRow) return dayByMessageId[row.key]
    }
    return null
}

/** True when a date or unread row sits strictly between the two messages. */
fun separatorBetween(rows: List<TimelineRow>, messageId: String, neighborId: String?): Boolean {
    if (neighborId == null) return true
    val from = rows.indexOfFirst { it.key == messageId }
    val to = rows.indexOfFirst { it.key == neighborId }
    if (from < 0 || to < 0 || from == to) return true
    val low = minOf(from, to)
    val high = maxOf(from, to)
    return rows.subList(low + 1, high).any { it is DateSeparatorRow || it is UnreadSeparatorRow }
}

/** Same side and no separator between them. */
fun bubbleSharesEdge(previousRole: String?, role: String, separated: Boolean): Boolean {
    if (separated || previousRole == null) return false
    val previousUser = previousRole.equals("user", ignoreCase = true)
    val user = role.equals("user", ignoreCase = true)
    return previousUser == user
}

data class BubbleNeighborEdges(
    val previousRole: String?,
    val nextRole: String?,
    val flatTop: Boolean,
    val flatBottom: Boolean,
    val showAvatar: Boolean,
)

/**
 * Neighbor edges for real message bubbles, once per painted row list.
 * A MessageRow missing from [roleByKey] is a process neighbor: it counts as
 * a message neighbor, but its role is null so the next bubble does not share
 * an edge. Only keys present in [roleByKey] are emitted.
 */
fun bubbleNeighborEdges(
    rows: List<TimelineRow>,
    roleByKey: Map<String, String>,
): Map<String, BubbleNeighborEdges> {
    val messageIndexes = ArrayList<Int>()
    for (index in rows.indices) {
        if (rows[index] is MessageRow) messageIndexes.add(index)
    }
    return buildMap {
        for (position in messageIndexes.indices) {
            val index = messageIndexes[position]
            val key = rows[index].key
            val role = roleByKey[key] ?: continue
            val previousKey = if (position == 0) null else rows[messageIndexes[position - 1]].key
            val nextKey = if (position == messageIndexes.lastIndex) null else rows[messageIndexes[position + 1]].key
            val previousRole = previousKey?.let { roleByKey[it] }
            val nextRole = nextKey?.let { roleByKey[it] }
            val separatedAbove = separatorBetween(rows, key, previousKey)
            val separatedBelow = separatorBetween(rows, key, nextKey)
            val flatTop = bubbleSharesEdge(previousRole, role, separatedAbove)
            put(
                key,
                BubbleNeighborEdges(
                    previousRole = previousRole,
                    nextRole = nextRole,
                    flatTop = flatTop,
                    flatBottom = bubbleSharesEdge(role, nextRole.orEmpty(), separatedBelow),
                    showAvatar = !flatTop,
                ),
            )
        }
    }
}
