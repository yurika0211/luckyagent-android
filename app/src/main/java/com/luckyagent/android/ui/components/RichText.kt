package com.luckyagent.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.node.HtmlInline
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.ParsedInline
import org.jsoup.nodes.Entities

private val markdownParser = Parser.builder().customInlineContentParserFactory(object : InlineContentParserFactory {
    override fun getTriggerCharacters() = setOf('$')
    override fun create() = InlineContentParser { state ->
        val scanner = state.scanner()
        val start = scanner.position()
        scanner.next()
        val fence = if (scanner.next('$')) "$$" else "$"
        val body = StringBuilder()
        var closed = false
        while (scanner.hasNext()) {
            if (fence == "$" && scanner.peek() == '\n') break
            if (scanner.next(fence)) { closed = true; break }
            val char = scanner.peek()
            body.append(char)
            scanner.next()
            if (char == '\\' && scanner.hasNext()) {
                body.append(scanner.peek()); scanner.next()
            }
        }
        if (closed && body.isNotBlank()) {
            val node = HtmlInline().apply { literal = "<span data-lucky-math='$fence'>${Entities.escape(body.toString())}</span>" }
            ParsedInline.of(node, scanner.position())
        } else { scanner.setPosition(start); ParsedInline.none() }
    }
}).build()
private val markdownRenderer = HtmlRenderer.builder().softbreak("\n").build()
private val mathPattern = Regex("""\$\$([\s\S]+?)\$\$|(?<!\\)\$((?:\\\$|[^$\n])+?)(?<!\\)\$""")

/** Markdown is parsed once; HTML nodes and code literals then share the native painter. */
internal fun richInlineText(
    source: String,
    colors: Map<String, Color> = emptyMap(),
    baseUrl: String = "",
): AnnotatedString = htmlAnnotatedText(richTextNodes(source), colors, baseUrl, parseMath = false)

internal fun richTextNodes(source: String): List<HtmlNode> =
    parseHtml(markdownRenderer.render(markdownParser.parse(source))).orEmpty()

internal fun htmlLiteralText(nodes: List<HtmlNode>): String = nodes.joinToString("") {
    when (it) {
        is HtmlNode.Text -> it.value
        is HtmlNode.Element -> if (it.tag == "br") "\n" else htmlLiteralText(it.children)
    }
}

internal fun htmlAnnotatedText(
    nodes: List<HtmlNode>,
    colors: Map<String, Color> = emptyMap(),
    baseUrl: String = "",
    parseMath: Boolean = true,
): AnnotatedString {
    val result = buildAnnotatedString {
        var lastWasBreak = true
        fun text(value: String) {
            append(value)
            if (value.isNotEmpty()) lastWasBreak = value.last() == '\n'
        }
        fun lineBreak() { if (length > 0 && !lastWasBreak) text("\n") }
        fun visit(node: HtmlNode, literal: Boolean = false) {
            when (node) {
                is HtmlNode.Text -> {
                    if (literal || !parseMath) text(node.value) else {
                        var cursor = 0
                        for (match in mathPattern.findAll(node.value)) {
                            text(node.value.substring(cursor, match.range.first))
                            val latex = match.groupValues[1].ifEmpty { match.groupValues[2] }.replace("\\$", "$")
                            pushStringAnnotation("math", if (match.value.startsWith("$$")) "display" else "inline")
                            text(latex)
                            pop()
                            cursor = match.range.last + 1
                        }
                        text(node.value.substring(cursor))
                    }
                }
                is HtmlNode.Element -> {
                    val mathFence = node.attributes["data-lucky-math"]
                    if (mathFence != null) {
                        fun plain(nodes: List<HtmlNode>): String = nodes.joinToString("") {
                            when (it) { is HtmlNode.Text -> it.value; is HtmlNode.Element -> plain(it.children) }
                        }
                        if (!literal) pushStringAnnotation("math", if (mathFence == "$$") "display" else "inline")
                        text(if (literal) mathFence + plain(node.children) + mathFence else plain(node.children).replace("\\$", "$"))
                        if (!literal) pop()
                        return
                    }
                    if (node.tag == "br") { text("\n"); return }
                    if (node.tag == "img") { text(node.attributes["alt"].orEmpty()); return }
                    val block = node.tag in setOf("p", "div", "blockquote", "pre", "li", "h1", "h2", "h3", "h4", "h5", "h6")
                    if (block) lineBreak()
                    val href = node.href?.let { resolveHtmlUrl(it, baseUrl) }
                    if (href != null) pushStringAnnotation("url", href)
                    withStyle(htmlSpanStyle(node, colors, href != null)) {
                        node.children.forEach { visit(it, literal || node.tag == "code" || node.tag == "pre") }
                    }
                    if (href != null) pop()
                    if (block) lineBreak()
                }
            }
        }
        nodes.forEach { visit(it) }
    }
    // Only layout separators are trimmed; escaped text is never parsed a second time.
    return result.subSequence(0, result.text.trimEnd('\n').length)
}

internal fun htmlCss(node: HtmlNode.Element): Map<String, String> = node.attributes["style"].orEmpty()
    .split(';').mapNotNull {
        val parts = it.split(':', limit = 2)
        if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim().lowercase() else null
    }.toMap()

internal fun htmlSpanStyle(node: HtmlNode.Element, colors: Map<String, Color>, link: Boolean = false): SpanStyle {
    val css = htmlCss(node)
    val decoration = buildList {
        if (node.tag == "u" || link || css["text-decoration"].orEmpty().contains("underline")) add(TextDecoration.Underline)
        if (node.tag in setOf("s", "del", "strike") || css["text-decoration"].orEmpty().contains("line-through")) add(TextDecoration.LineThrough)
    }
    val code = node.tag == "code" || node.tag == "pre"
    return SpanStyle(
        color = parseHtmlColor(css["color"] ?: node.attributes["color"])
            ?: node.color?.let { colors[it.name.lowercase()] }
            ?: if (link) colors["accent"] ?: Color(0xFF3F8A37) else Color.Unspecified,
        background = parseHtmlColor(css["background-color"] ?: css["background"])
            ?: when { node.tag == "mark" -> Color(0x55FFE082); code -> Color(0x143F8A37); else -> Color.Unspecified },
        fontWeight = when {
            node.tag in setOf("b", "strong", "th") || css["font-weight"] in setOf("bold", "bolder") -> FontWeight.Bold
            css["font-weight"]?.toIntOrNull() != null -> FontWeight(css.getValue("font-weight").toInt().coerceIn(1, 1000))
            else -> null
        },
        fontStyle = if (node.tag in setOf("i", "em") || css["font-style"] == "italic") FontStyle.Italic else null,
        fontFamily = if (code || css["font-family"].orEmpty().contains("monospace")) FontFamily.Monospace else null,
        fontSize = css["font-size"]?.removeSuffix("px")?.removeSuffix("pt")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(10f, 48f)?.sp
            ?: if (node.tag in setOf("small", "sub", "sup")) 12.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        baselineShift = when (node.tag) { "sub" -> BaselineShift.Subscript; "sup" -> BaselineShift.Superscript; else -> null },
        textDecoration = decoration.takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
    )
}

internal fun parseHtmlColor(value: String?): Color? {
    val text = value?.trim()?.lowercase() ?: return null
    val named = mapOf("red" to 0xFFFF0000, "green" to 0xFF008000, "blue" to 0xFF0000FF,
        "black" to 0xFF000000, "white" to 0xFFFFFFFF, "gray" to 0xFF808080, "grey" to 0xFF808080,
        "orange" to 0xFFFFA500, "yellow" to 0xFFFFFF00, "purple" to 0xFF800080, "transparent" to 0L)
    named[text]?.let { return Color(it) }
    if (text.startsWith('#')) {
        val hex = text.drop(1)
        val expanded = if (hex.length == 3 || hex.length == 4) hex.map { "$it$it" }.joinToString("") else hex
        val number = expanded.toLongOrNull(16) ?: return null
        return when (expanded.length) {
            6 -> Color(number or 0xFF000000)
            8 -> Color((number ushr 8) or ((number and 255) shl 24)) // CSS #RRGGBBAA
            else -> null
        }
    }
    val rgb = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""").matchEntire(text) ?: return null
    val components = (1..3).map { rgb.groupValues[it].toIntOrNull()?.coerceIn(0, 255) ?: return null }
    return Color(components[0], components[1], components[2],
        ((rgb.groupValues[4].toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * 255).toInt())
}
