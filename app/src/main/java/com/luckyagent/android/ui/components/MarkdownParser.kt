package com.luckyagent.android.ui.components

/**
 * Blocks understood by the lightweight Markdown renderer.
 *
 * Tables follow the GitHub Flavored Markdown shape: a header row immediately
 * followed by a delimiter row. Cell splitting is stateful, so escaped pipes,
 * pipes in code spans, and pipes in link destinations stay inside their cell.
 */
internal sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class ListItem(val text: String, val marker: String = "•") : MdBlock()
    data class Code(val body: String, val lang: String = "") : MdBlock()
    data class Html(val source: String) : MdBlock()
    data class Mermaid(val source: String) : MdBlock()
    data class Rule(val marker: String = "---") : MdBlock()
    data class Math(val latex: String, val display: Boolean) : MdBlock()
    data class Image(val alt: String, val source: String) : MdBlock()
    data class Table(
        val rows: List<List<String>>,
        val alignments: List<TableAlignment>,
    ) : MdBlock()

    /** A likely table is still missing a complete delimiter row while typing. */
    data object IncompleteTable : MdBlock()
}

internal enum class TableAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

/**
 * Splits Markdown into the block model used by [MarkdownText].
 *
 * [hideIncompleteTables] is used for streaming assistant/tool output. A
 * header-looking line is held until its delimiter row is complete, preventing
 * a transient `| ... |` fragment from being shown as an ugly paragraph.
 */
internal fun splitMarkdownBlocks(
    src: String,
    hideIncompleteTables: Boolean = false,
): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        val text = para.toString().trimEnd()
        if (text.isNotBlank()) out += MdBlock.Paragraph(text)
        para.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)
        if (next != null && hasTableDelimiterCandidate(line) && isTableSeparator(next)) {
            val header = splitTableRow(line)
            val delimiter = splitTableRow(next)
            val table = buildTable(header, delimiter)
            if (table != null) {
                flushPara()
                val rows = mutableListOf(header)
                i += 2
                while (i < lines.size && lines[i].isNotBlank() && hasTableDelimiterCandidate(lines[i])) {
                    rows += normalizeTableRow(splitTableRow(lines[i]), table.alignments.size)
                    i++
                }
                out += table.copy(rows = rows)
                continue
            }
        }

        if (hideIncompleteTables && isIncompleteTable(lines, i)) {
            flushPara()
            out += MdBlock.IncompleteTable
            i = consumeIncompleteTable(lines, i)
            continue
        }

        val image = Regex("""^!\[([^]]*)\]\((.+)\)$""").matchEntire(line.trim())
        if (image != null) {
            flushPara()
            out += MdBlock.Image(image.groupValues[1], image.groupValues[2])
            i++
            continue
        }
        val bareImage = Regex("""^\s*(https?://\S+)\s*$""").matchEntire(line)
        if (bareImage != null && looksLikeImageUrl(bareImage.groupValues[1])) {
            flushPara()
            out += MdBlock.Image("图片", bareImage.groupValues[1])
            i++
            continue
        }
        if (line.trimStart().startsWith("$$")) {
            flushPara()
            val opener = line.trim()
            val sameLine = opener.removePrefix("$$").removeSuffix("$$").trim()
            if (opener.length > 4 && opener.endsWith("$$") && sameLine.isNotEmpty()) {
                out += MdBlock.Math(sameLine, display = true)
                i++
                continue
            }
            i++
            val math = StringBuilder()
            if (opener.length > 2) math.append(opener.removePrefix("$$"))
            while (i < lines.size && !lines[i].trimEnd().endsWith("$$")) {
                if (math.isNotEmpty()) math.append('\n')
                math.append(lines[i])
                i++
            }
            if (i < lines.size) {
                val closing = lines[i].trimEnd()
                val before = closing.removeSuffix("$$")
                if (before.isNotBlank()) {
                    if (math.isNotEmpty()) math.append('\n')
                    math.append(before)
                }
                i++
            }
            out += MdBlock.Math(math.toString().trim(), display = true)
            continue
        }
        if (isThematicBreak(line)) {
            flushPara()
            out += MdBlock.Rule(line.trim())
            i++
            continue
        }
        if (line.trimStart().startsWith("```")) {
            flushPara()
            val lang = fenceLang(line)
            i++
            val code = StringBuilder()
            var closed = false
            while (i < lines.size) {
                if (isClosingFence(lines[i])) {
                    closed = true
                    i++
                    break
                }
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            val body = code.toString()
            out += when {
                !closed -> MdBlock.Code(body, lang)
                lang == "mermaid" -> MdBlock.Mermaid(body)
                lang == "html" && parseHtml(body) != null -> MdBlock.Html(body)
                else -> MdBlock.Code(body, lang)
            }
            continue
        }
        val heading = Regex("""^(#{1,6})\s+(.*)$""").matchEntire(line.trimEnd())
        if (heading != null) {
            flushPara()
            out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            i++
            continue
        }
        val list = Regex("""^\s*[-*]\s+(.*)$""").matchEntire(line)
        if (list != null) {
            flushPara()
            out += MdBlock.ListItem(list.groupValues[1])
            i++
            continue
        }
        val orderedList = Regex("""^\s*(\d+)[.)]\s+(.*)$""").matchEntire(line)
        if (orderedList != null) {
            flushPara()
            out += MdBlock.ListItem(
                text = orderedList.groupValues[2],
                marker = "${orderedList.groupValues[1]}.",
            )
            i++
            continue
        }
        if (line.isBlank()) {
            flushPara()
            i++
            continue
        }
        if (para.isEmpty() && line.trimStart().startsWith("<")) {
            val block = takeHtmlBlock(lines, i)
            if (block != null) {
                flushPara()
                out += MdBlock.Html(block.first)
                i = block.second
                continue
            }
        }
        if (para.isNotEmpty()) para.append('\n')
        para.append(line)
        i++
    }
    flushPara()
    return out
}

/** CommonMark thematic break: 3 or more -, *, or _ with optional spaces. */
internal fun isThematicBreak(line: String): Boolean {
    val token = line.trim()
    if (token.length < 3) return false
    val marker = token.first()
    if (marker != '-' && marker != '*' && marker != '_') return false
    return token.all { it == marker || it == ' ' || it == '\t' } && token.count { it == marker } >= 3
}

private fun buildTable(header: List<String>, delimiter: List<String>): MdBlock.Table? {
    if (header.isEmpty() || delimiter.size != header.size) return null
    val alignments = delimiter.map { parseAlignment(it) ?: return null }
    return MdBlock.Table(
        rows = listOf(header),
        alignments = alignments,
    )
}

private fun parseAlignment(cell: String): TableAlignment? {
    val token = cell.trim()
    if (!Regex("^:?-{3,}:?$").matches(token)) return null
    val left = token.startsWith(':')
    val right = token.endsWith(':')
    return when {
        left && right -> TableAlignment.CENTER
        right -> TableAlignment.RIGHT
        else -> TableAlignment.LEFT
    }
}

private fun normalizeTableRow(row: List<String>, columnCount: Int): List<String> =
    row.take(columnCount).let { values ->
        values + List((columnCount - values.size).coerceAtLeast(0)) { "" }
    }

/**
 * A delimiter row must have at least one valid cell. A one-column table is
 * supported when it uses explicit outer pipes (`| value |` / `| --- |`).
 */
private fun isTableSeparator(line: String): Boolean {
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { parseAlignment(it) != null }
}

private fun hasTableDelimiterCandidate(line: String): Boolean =
    splitTableRow(line).size > 1 || line.trim().startsWith("|") || line.trim().endsWithUnescapedPipe()

private fun isIncompleteTable(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    if (!hasTableDelimiterCandidate(line)) return false
    val next = lines.getOrNull(index + 1)
    if (next == null || next.isBlank()) return true
    if (!hasTableDelimiterCandidate(next)) return false
    // A partial delimiter commonly arrives as `| ---` before its final cells.
    val nextTrimmed = next.trim()
    return nextTrimmed.any { it == '-' || it == ':' } &&
        (nextTrimmed.contains('|') || splitTableRow(next).size > 1)
}

private fun consumeIncompleteTable(lines: List<String>, start: Int): Int {
    var i = start + 1
    while (i < lines.size && lines[i].isNotBlank() && hasTableDelimiterCandidate(lines[i])) i++
    return i
}

/**
 * Splits one table row without treating a pipe as a delimiter while inside a
 * code span or a Markdown link destination. `\\|` is unescaped to `|`.
 */
internal fun splitTableRow(line: String): List<String> {
    val raw = line.trim()
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inCode = false
    var linkDestinationDepth = 0
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch == '\\' && i + 1 < raw.length && raw[i + 1] == '|') {
            current.append('|')
            i += 2
            continue
        }
        if (ch == '`') {
            inCode = !inCode
            current.append(ch)
            i++
            continue
        }
        if (!inCode) {
            if (ch == ']' && i + 1 < raw.length && raw[i + 1] == '(') {
                linkDestinationDepth++
                current.append(ch)
                current.append('(')
                i += 2
                continue
            }
            if (ch == ')' && linkDestinationDepth > 0) {
                linkDestinationDepth--
                current.append(ch)
                i++
                continue
            }
        }
        if (ch == '|' && !inCode && linkDestinationDepth == 0) {
            cells += current.toString().trim()
            current.clear()
        } else {
            current.append(ch)
        }
        i++
    }
    cells += current.toString().trim()

    if (raw.startsWith("|") && cells.isNotEmpty()) cells.removeAt(0)
    if (raw.endsWithUnescapedPipe() && cells.isNotEmpty()) cells.removeAt(cells.lastIndex)
    return cells
}

private fun String.endsWithUnescapedPipe(): Boolean {
    if (!endsWith('|')) return false
    var slashCount = 0
    var i = length - 2
    while (i >= 0 && this[i] == '\\') {
        slashCount++
        i--
    }
    return slashCount % 2 == 0
}

private fun looksLikeImageUrl(source: String): Boolean {
    val path = source.substringBefore('?').substringBefore('#').lowercase()
    return path.matches(Regex(".*\\.(png|jpe?g|gif|webp|bmp|svg|avif|heic)$")) ||
        source.contains("format=", ignoreCase = true) ||
        source.contains("image", ignoreCase = true)
}


/** First word of a fence opener: ```mermaid title -> mermaid. */

/** A closing fence is a line of backticks and nothing else. */
internal fun isClosingFence(line: String): Boolean {
    val token = line.trim()
    return token == "```"
}

internal fun fenceLang(opener: String): String =
    opener.trim().removePrefix("```").trim().substringBefore(' ').substringBefore('\t').lowercase()

/**
 * A run of lines whose top-level tag is allowed HTML, consumed as one block.
 * Inline tags inside a sentence stay in the paragraph. Returns null when the
 * run is not renderable HTML, so the caller keeps it as text.
 */
private fun takeHtmlBlock(lines: List<String>, start: Int): Pair<String, Int>? {
    val first = lines[start].trimStart()
    val tag = Regex("""^<([A-Za-z]+)""").find(first)?.groupValues?.get(1)?.lowercase() ?: return null
    if (tag !in setOf("details", "p")) return null
    val close = "</$tag>"
    val buf = StringBuilder()
    var i = start
    while (i < lines.size && lines[i].isNotBlank()) {
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(lines[i])
        i++
        if (buf.toString().contains(close, ignoreCase = true)) break
    }
    val source = buf.toString()
    if (!source.contains(close, ignoreCase = true)) return null
    if (parseHtml(source) == null) return null
    return source to i
}
