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

/** Same side and no separator between them. */
fun bubbleSharesEdge(previousRole: String?, role: String, separated: Boolean): Boolean {
    if (separated || previousRole == null) return false
    val previousUser = previousRole.equals("user", ignoreCase = true)
    val user = role.equals("user", ignoreCase = true)
    return previousUser == user
}
