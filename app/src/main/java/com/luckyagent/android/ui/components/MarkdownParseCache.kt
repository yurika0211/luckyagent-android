package com.luckyagent.android.ui.components

import java.util.LinkedHashMap

internal data class ParsedMarkdown(
    val blocks: List<MdBlock>,
    val htmlNodes: List<List<HtmlNode>>,
    val richNodes: Map<String, List<HtmlNode>>,
    val svgNodes: List<SvgDocument?>,
)

/**
 * Keeps the expensive, pure Markdown/CommonMark projection off the Compose
 * thread. Completed messages are revisited when history is refreshed, so a
 * small LRU also avoids parsing the same body again.
 */
internal object MarkdownParseCache {
    private const val MAX_ENTRIES = 24

    private data class Key(
        val source: String,
        val streaming: Boolean,
    )

    private val entries = LinkedHashMap<Key, ParsedMarkdown>(
        MAX_ENTRIES,
        0.75f,
        true,
    )

    fun getOrParse(source: String, streaming: Boolean): ParsedMarkdown {
        val key = Key(source, streaming)
        synchronized(entries) {
            entries[key]?.let { return it }
        }

        val blocks = splitMarkdownBlocks(source, hideIncompleteTables = streaming)
        val htmlNodes = blocks.map { block ->
            if (block is MdBlock.Html && !requiresHtmlPage(block.source)) {
                parseHtml(block.source).orEmpty()
            } else {
                emptyList()
            }
        }
        val svgNodes = blocks.map { block ->
            if (block is MdBlock.Html) staticSvgDocument(block.source) else null
        }

        val richSources = linkedSetOf<String>()
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> richSources += block.text
                is MdBlock.ListItem -> richSources += block.text
                is MdBlock.Paragraph -> richSources += block.text
                is MdBlock.Table -> block.rows.flatten().forEach(richSources::add)
                else -> Unit
            }
        }
        val parsed = ParsedMarkdown(
            blocks = blocks,
            htmlNodes = htmlNodes,
            richNodes = richSources.associateWith(::richTextNodes),
            svgNodes = svgNodes,
        )

        synchronized(entries) {
            entries[key] = parsed
            while (entries.size > MAX_ENTRIES) {
                entries.remove(entries.entries.first().key)
            }
        }
        return parsed
    }
}
