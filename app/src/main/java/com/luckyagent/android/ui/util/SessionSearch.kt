package com.luckyagent.android.ui.util

enum class SessionSearchTab { Messages, Media, Links }

/** A hit identifies the actual painted row, including folded process rows. */
data class SessionSearchHit(
    val rowKey: String,
    val label: String,
    val tab: SessionSearchTab,
)

data class SearchableMessage(
    val id: String,
    val text: String,
    val fileNames: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    /** A folded row can refer to several full bodies without joining/copying them. */
    val additionalTexts: List<String> = emptyList(),
)

private val linkPattern = Regex("""https?://[^\s)]+""")

fun searchableLinks(text: String): List<String> = linkPattern.findAll(text).map { it.value }.toList()

private fun searchExcerpt(text: String, needle: String): String {
    val match = text.indexOf(needle, ignoreCase = true).coerceAtLeast(0)
    val start = (match - 24).coerceAtLeast(0)
    val end = (start + 80).coerceAtMost(text.length)
    val excerpt = text.substring(start, end).replace('\n', ' ').replace('\r', ' ')
    return (if (start > 0) "…" else "") + excerpt + (if (end < text.length) "…" else "")
}

/** Match full search bodies; rendering limits and display row identity stay independent. */
fun sessionSearchHits(messages: List<SearchableMessage>, query: String, tab: SessionSearchTab): List<SessionSearchHit> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return messages.mapNotNull { message ->
        val bodies = sequenceOf(message.text) + message.additionalTexts.asSequence()
        val label = when (tab) {
            SessionSearchTab.Messages -> {
                val body = bodies.firstOrNull { it.contains(needle, ignoreCase = true) } ?: return@mapNotNull null
                searchExcerpt(body, needle)
            }
            SessionSearchTab.Media -> {
                val name = message.fileNames.firstOrNull { it.contains(needle, ignoreCase = true) } ?: return@mapNotNull null
                searchExcerpt(name, needle)
            }
            SessionSearchTab.Links -> {
                val link = (message.links.asSequence() + bodies.flatMap { linkPattern.findAll(it).map { match -> match.value } })
                    .firstOrNull { it.contains(needle, ignoreCase = true) } ?: return@mapNotNull null
                searchExcerpt(link, needle)
            }
        }
        SessionSearchHit(message.id, label, tab)
    }
}
