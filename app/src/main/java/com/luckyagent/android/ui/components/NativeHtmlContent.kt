package com.luckyagent.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.theme.CloverLine

@Composable
internal fun NativeHtmlContent(
    source: String, color: Color, headers: Map<String, String>, baseUrl: String,
    prepared: List<HtmlNode>? = null, details: HtmlDetailsContext? = null,
) {
    val nodes = prepared ?: remember(source) { parseHtml(source).orEmpty() }
    HtmlFlow(nodes, color, headers, baseUrl, MaterialTheme.typography.bodyLarge.copy(color = color), details)
}

private val blockTags = setOf("p", "div", "section", "article", "header", "footer", "main", "blockquote", "pre", "ul", "ol", "li",
    "h1", "h2", "h3", "h4", "h5", "h6", "details", "table", "img", "hr")

private fun HtmlNode.hasBlock(): Boolean = this is HtmlNode.Element &&
    (tag in blockTags || children.any { it.hasBlock() })

/** Consecutive inline nodes stay together, preserving nested spans and link ranges. */
@Composable
internal fun HtmlFlow(
    nodes: List<HtmlNode>, color: Color, headers: Map<String, String>, baseUrl: String, style: TextStyle,
    details: HtmlDetailsContext? = null,
) {
    if (details == null) {
        val state = rememberSaveable(saver = HtmlDetailsState.Saver) { HtmlDetailsState() }
        val context = remember(nodes, state) { HtmlDetailsContext(state, state.identities(nodes)) }
        HtmlFlow(nodes, color, headers, baseUrl, style, context)
        return
    }
    val groups = remember(nodes, details) {
        buildList<List<HtmlNode>> {
            var inline = mutableListOf<HtmlNode>()
            nodes.forEach { node ->
                if (node.hasBlock()) {
                    if (inline.isNotEmpty()) add(inline)
                    inline = mutableListOf()
                    add(listOf(node))
                } else inline += node
            }
            if (inline.isNotEmpty()) add(inline)
        }
    }
    Column {
        groups.forEachIndexed { index, group ->
            val node = group.singleOrNull() as? HtmlNode.Element
            val groupKey = node?.let { details.identities[it] } ?: index
            key(groupKey) {
                if (node != null && node.hasBlock()) HtmlElementView(node, color, headers, baseUrl, style, details)
                else {
                    val palette = htmlColorPalette()
                    val text = remember(group, palette, baseUrl) {
                        val annotated = htmlAnnotatedText(group, palette, baseUrl)
                        val start = annotated.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                        val end = annotated.text.indexOfLast { !it.isWhitespace() } + 1
                        annotated.subSequence(start.coerceAtMost(end), end)
                    }
                    if (text.isNotBlank()) RichAnnotatedText(text, style, color)
                }
            }
        }
    }
}

@Composable
private fun HtmlElementView(
    node: HtmlNode.Element, color: Color, headers: Map<String, String>, baseUrl: String, parent: TextStyle,
    details: HtmlDetailsContext,
) {
    val palette = htmlColorPalette()
    val span = htmlSpanStyle(node, palette)
    val css = htmlCss(node)
    val paragraph = ParagraphStyle(textAlign = when (css["text-align"] ?: node.attributes["align"]) {
        "center" -> TextAlign.Center
        "right", "end" -> TextAlign.End
        else -> TextAlign.Start
    })
    val style = parent.copy(
        color = span.color.takeIf { it != Color.Unspecified } ?: parent.color,
        background = span.background.takeIf { it != Color.Unspecified } ?: parent.background,
        fontSize = span.fontSize.takeIf { it != androidx.compose.ui.unit.TextUnit.Unspecified } ?: parent.fontSize,
        fontWeight = span.fontWeight ?: parent.fontWeight,
        fontStyle = span.fontStyle ?: parent.fontStyle,
        fontFamily = span.fontFamily ?: parent.fontFamily,
        baselineShift = span.baselineShift ?: parent.baselineShift,
        textDecoration = span.textDecoration ?: parent.textDecoration,
        textAlign = paragraph.textAlign,
    )
    val foreground = span.color.takeIf { it != Color.Unspecified } ?: color
    when (node.tag) {
        "img" -> MarkdownImage(node.attributes["src"].orEmpty(), node.attributes["alt"].orEmpty(), headers, baseUrl)
        "hr" -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CloverLine)
        "pre" -> CodeText(htmlLiteralText(node.children))
        "details" -> {
            val summary = node.children.firstOrNull { it is HtmlNode.Element && it.tag == "summary" } as? HtmlNode.Element
            val detailsKey = details.identities.getValue(node)
            key(detailsKey) {
                val initiallyOpen = "open" in node.attributes
                val expanded = details.state.isExpanded(detailsKey, initiallyOpen)
                TextButton(onClick = { details.state.toggle(detailsKey, initiallyOpen) }) {
                    Text(if (expanded) "▾ " else "▸ ", color = foreground)
                    Text(htmlAnnotatedText(summary?.children ?: listOf(HtmlNode.Text("详情")), palette, baseUrl), color = foreground)
                }
                if (expanded) HtmlFlow(node.children.filterNot { it === summary }, foreground, headers, baseUrl, style, details)
            }
        }
        "ul", "ol" -> Column(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
            var number = node.attributes["start"]?.toIntOrNull() ?: 1
            node.children.filterIsInstance<HtmlNode.Element>().forEachIndexed { index, child -> key(index) {
                if (child.tag == "li") Row(Modifier.padding(vertical = 2.dp)) {
                    Text(if (node.tag == "ol") "${number++}. " else "• ", color = foreground, style = style)
                    Box(Modifier.weight(1f)) { HtmlFlow(child.children, foreground, headers, baseUrl, style, details) }
                } else HtmlElementView(child, foreground, headers, baseUrl, style, details)
            } }
        }
        "table" -> HtmlTable(node, foreground, headers, baseUrl, style, details)
        else -> {
            val heading = when (node.tag) {
                "h1" -> MaterialTheme.typography.titleLarge
                "h2" -> MaterialTheme.typography.titleMedium
                "h3", "h4", "h5", "h6" -> MaterialTheme.typography.titleSmall
                else -> null
            }
            val modifier = Modifier.fillMaxWidth().padding(vertical = if (heading != null) 6.dp else 2.dp)
                .then(if (node.tag == "blockquote") Modifier.border(1.dp, CloverLine).padding(8.dp) else Modifier)
                .then(if (span.background != Color.Unspecified) Modifier.background(span.background).padding(4.dp) else Modifier)
            Column(modifier) { HtmlFlow(node.children, foreground, headers, baseUrl,
                if (heading != null) style.copy(fontSize = heading.fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else style, details) }
        }
    }
}

@Composable
private fun HtmlTable(
    node: HtmlNode.Element, color: Color, headers: Map<String, String>, baseUrl: String, style: TextStyle,
    details: HtmlDetailsContext,
) {
    fun rows(nodes: List<HtmlNode>): List<HtmlNode.Element> = nodes.filterIsInstance<HtmlNode.Element>().flatMap {
        when (it.tag) { "tr" -> listOf(it); "thead", "tbody", "tfoot" -> rows(it.children); else -> emptyList() }
    }
    val rows = remember(node, details) { rows(node.children) }
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        rows.forEach { row -> Row {
            row.children.filterIsInstance<HtmlNode.Element>().filter { it.tag == "td" || it.tag == "th" }.forEach { cell ->
                val colspan = (cell.attributes["colspan"]?.toIntOrNull() ?: 1).coerceIn(1, 8)
                Box(Modifier.width((160 * colspan).dp).border(1.dp, CloverLine).padding(8.dp)) {
                    HtmlElementView(cell, color, headers, baseUrl, style, details)
                }
            }
        } }
    }
}
