package com.luckyagent.android.ui.util

enum class SessionSearchTab { Messages, Media, Links }

data class SessionSearchHit(
    val messageId: String,
    val label: String,
    val tab: SessionSearchTab,
)

data class SearchableMessage(
    val id: String,
    val text: String,
    val fileNames: List<String> = emptyList(),
    val links: List<String> = emptyList(),
)

private val linkPattern = Regex("""https?://[^\s)]+""")

fun searchableLinks(text: String): List<String> = linkPattern.findAll(text).map { it.value }.toList()

fun sessionSearchHits(messages: List<SearchableMessage>, query: String, tab: SessionSearchTab): List<SessionSearchHit> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return messages.mapNotNull { message ->
        when (tab) {
            SessionSearchTab.Messages -> {
                if (!message.text.contains(needle, ignoreCase = true)) return@mapNotNull null
                SessionSearchHit(message.id, message.text.lineSequence().firstOrNull().orEmpty().take(80), tab)
            }
            SessionSearchTab.Media -> {
                val name = message.fileNames.firstOrNull { it.contains(needle, ignoreCase = true) } ?: return@mapNotNull null
                SessionSearchHit(message.id, name, tab)
            }
            SessionSearchTab.Links -> {
                val link = message.links.firstOrNull { it.contains(needle, ignoreCase = true) } ?: return@mapNotNull null
                SessionSearchHit(message.id, link, tab)
            }
        }
    }
}
