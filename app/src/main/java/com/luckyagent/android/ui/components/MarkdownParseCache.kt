package com.luckyagent.android.ui.components

import com.luckyagent.android.perf.MemoryProbe
import com.luckyagent.android.perf.MemorySample
import com.luckyagent.android.perf.PerfProbe
import com.luckyagent.android.perf.PerfVariant
import java.util.LinkedHashMap

internal data class ParsedMarkdown(
    val blocks: List<MdBlock>,
    val htmlNodes: List<List<HtmlNode>>,
    val richNodes: Map<String, List<HtmlNode>>,
) {
    /** WebView / native drawing blocks. Plain text replies stay false. */
    val needsLiveSurface: Boolean = blocks.any(::blockNeedsLiveSurface)
}

/**
 * Blocks that keep a WebView or a native drawing surface alive.
 * The chat list pauses those surfaces once the row leaves the window.
 */
internal fun blockNeedsLiveSurface(block: MdBlock): Boolean = when (block) {
    is MdBlock.Mermaid -> true
    is MdBlock.Html -> block.showPreview || requiresHtmlPage(block.source) || staticSvgDocument(block.source) != null
    is MdBlock.Math -> true
    else -> false
}

/**
 * Keeps the expensive, pure Markdown/CommonMark projection off the Compose
 * thread. Completed messages are revisited when history is refreshed, so a
 * small LRU also avoids parsing the same body again.
 *
 * The map key is a content hash plus length, not the source string. A long
 * assistant reply should not be retained twice just to look itself up.
 */
internal object MarkdownParseCache {
    const val MAX_ENTRIES = 256

    private data class Key(
        val hash: Int,
        val length: Int,
        val streaming: Boolean,
    )

    private class Slot(
        val source: String,
        val parsed: ParsedMarkdown,
    )

    private val entries = LinkedHashMap<Key, Slot>(
        MAX_ENTRIES,
        0.75f,
        true,
    )

    fun getOrParse(
        source: String,
        streaming: Boolean,
        probe: PerfProbe = PerfProbe.shared,
    ): ParsedMarkdown {
        val key = Key(source.hashCode(), source.length, streaming)
        synchronized(entries) {
            entries[key]?.takeIf { it.source == source }?.let { return it.parsed }
        }

        val parsed = probe.measure("markdown.parse", PerfVariant.TREATMENT, source.length) {
            parse(source, streaming)
        }

        synchronized(entries) {
            val raced = entries[key]
            if (raced != null && raced.source == source) return raced.parsed
            entries[key] = Slot(source, parsed)
            while (entries.size > MAX_ENTRIES) {
                entries.remove(entries.entries.first().key)
            }
        }
        return parsed
    }

    internal fun parse(source: String, streaming: Boolean): ParsedMarkdown {
        val blocks = splitMarkdownBlocks(source, hideIncompleteTables = streaming)
        val htmlNodes = ArrayList<List<HtmlNode>>(blocks.size)
        val richSources = LinkedHashSet<String>()
        for (block in blocks) {
            htmlNodes += if (block is MdBlock.Html && !requiresHtmlPage(block.source)) {
                parseHtml(block.source).orEmpty()
            } else {
                emptyList()
            }
            when (block) {
                is MdBlock.Heading -> richSources += block.text
                is MdBlock.ListItem -> richSources += block.text
                is MdBlock.Paragraph -> richSources += block.text
                is MdBlock.Table -> block.rows.forEach { row -> row.forEach(richSources::add) }
                else -> Unit
            }
        }
        val richNodes = HashMap<String, List<HtmlNode>>(richSources.size)
        for (text in richSources) richNodes[text] = richTextNodes(text)
        return ParsedMarkdown(blocks, htmlNodes, richNodes)
    }

    internal fun clearForTest() {
        synchronized(entries) { entries.clear() }
    }

    internal fun sizeForTest(): Int = synchronized(entries) { entries.size }
}

/** Heap reading used when a caller wants the parse cost next to the result. */
internal fun markdownMemory(): MemorySample = MemoryProbe.runtime.sample()
