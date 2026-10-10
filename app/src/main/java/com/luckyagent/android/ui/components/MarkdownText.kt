package com.luckyagent.android.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2

/**
 * Lightweight Markdown renderer: bold, italic, inline code, fenced code blocks,
 * headings, bullet and ordered lists, links, and GFM tables. Tables use a stateful row
 * tokenizer (escaped/code/link pipes are safe), support alignment markers, and
 * use content-based column widths inside a horizontally scrollable row. One-column
 * tables are supported when written with explicit outer pipes. A table is held
 * back while [streaming] until its delimiter row is complete. Malformed
 * delimiter rows remain ordinary text; colspan and nested block Markdown in a
 * cell are outside this lightweight renderer's scope.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color = CloverText,
    modifier: Modifier = Modifier,
    imageHeaders: Map<String, String> = emptyMap(),
    imageBaseUrl: String = "",
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    val blocks = remember(markdown, streaming) {
        splitMarkdownBlocks(markdown, hideIncompleteTables = streaming)
    }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Mermaid -> {
                    MermaidView(source = block.source)
                }
                is MdBlock.Html -> {
                    HtmlBlock(source = block.source, color = color)
                }
                is MdBlock.Code -> {
                    SelectionContainer {
                        Text(
                            text = block.body,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = CloverText2,
                                lineHeight = 18.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(CloverBgSide, RoundedCornerShape(8.dp))
                                .border(1.dp, CloverLine, RoundedCornerShape(8.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }
                is MdBlock.Image -> {
                    val imageSource = remember(block.source, imageBaseUrl) {
                        resolveImageSource(block.source, imageBaseUrl)
                    }
                    val bitmap by produceState<android.graphics.Bitmap?>(null, block.source) {
                        value = withContext(Dispatchers.IO) { decodeMarkdownImage(block.source) }
                    }
                    val imageBitmap = bitmap?.asImageBitmap()
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = block.alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    } else if (!block.source.startsWith("data:image/")) {
                        coil.compose.AsyncImage(
                            model = remember(imageSource, imageBaseUrl, imageHeaders) {
                                ImageRequest.Builder(context)
                                    .data(imageSource)
                                    .apply {
                                        if (sameOrigin(imageSource, imageBaseUrl)) {
                                            imageHeaders.forEach { (name, value) -> addHeader(name, value) }
                                        }
                                    }
                                    .build()
                            },
                            contentDescription = block.alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                }
                is MdBlock.Table -> {
                    // Keep columns aligned while sizing each one from its longest
                    // visible cell. The bounds prevent a long URL from taking
                    // over the bubble; the surrounding scroll handles overflow.
                    val columnWidths = remember(block.rows) {
                        List(block.alignments.size) { column ->
                            val maxCharacters = block.rows.maxOfOrNull {
                                it.getOrNull(column).orEmpty().length
                            } ?: 0
                            (maxCharacters * 8 + 16).coerceIn(64, 280).dp
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        block.rows.forEachIndexed { rowIndex, cells ->
                            Row {
                                cells.forEachIndexed { columnIndex, cell ->
                                    Text(
                                        text = inlineMarkdown(cell),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = color,
                                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = when (block.alignments.getOrNull(columnIndex)) {
                                                TableAlignment.CENTER -> TextAlign.Center
                                                TableAlignment.RIGHT -> TextAlign.End
                                                else -> TextAlign.Start
                                            },
                                        ),
                                        modifier = Modifier
                                            .width(columnWidths.getOrElse(columnIndex) { 64.dp })
                                            .border(1.dp, CloverLine)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                MdBlock.IncompleteTable -> Unit
                is MdBlock.Rule -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(1.dp)
                            .background(CloverLine),
                    )
                }
                is MdBlock.Math -> {
                    LatexBlock(
                        latex = block.latex,
                        display = block.display,
                        color = color,
                    )
                }
                is MdBlock.Heading -> {
                    Text(
                        text = inlineMarkdown(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            3 -> MaterialTheme.typography.titleSmall
                            4 -> MaterialTheme.typography.bodyLarge
                            5 -> MaterialTheme.typography.bodyMedium
                            else -> MaterialTheme.typography.bodySmall
                        }.copy(color = color, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is MdBlock.ListItem -> {
                    Text(
                        text = buildAnnotatedString {
                            append(block.marker)
                            append(" ")
                            append(inlineMarkdown(block.text))
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(color = color),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
                is MdBlock.Paragraph -> {
                    RichMarkdownText(
                        text = block.text,
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun decodeMarkdownImage(source: String): android.graphics.Bitmap? = runCatching {
    val payload = source.substringAfter("base64,", "")
    if (payload.isBlank()) return null
    val bytes = Base64.decode(payload, Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

private fun resolveImageSource(source: String, baseUrl: String): String {
    if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:")) return source
    if (baseUrl.isBlank()) return source
    return baseUrl.trimEnd('/') + "/" + source.trimStart('/')
}

private fun sameOrigin(source: String, baseUrl: String): Boolean {
    val imageUri = runCatching { android.net.Uri.parse(source) }.getOrNull() ?: return false
    val baseUri = runCatching { android.net.Uri.parse(baseUrl) }.getOrNull() ?: return false
    return imageUri.scheme == baseUri.scheme &&
        imageUri.host.equals(baseUri.host, ignoreCase = true) &&
        (imageUri.port.takeIf { it >= 0 } ?: defaultPort(imageUri.scheme)) ==
        (baseUri.port.takeIf { it >= 0 } ?: defaultPort(baseUri.scheme))
}

private fun defaultPort(scheme: String?): Int = if (scheme.equals("https", true)) 443 else 80

@Composable
private fun RichMarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
) {
    val rendered = remember(text) { renderHtmlInline(text) }
    val pieces = remember(rendered) { splitInlineMath(rendered) }
    if (pieces.none { it.math }) {
        LinkedText(text = inlineMarkdown(rendered), style = style, color = color, modifier = modifier)
        return
    }
    Column(modifier) {
        pieces.chunkedByText().forEach { row ->
            if (row.size == 1 && row[0].math && row[0].displayFence) {
                LatexView(latex = row[0].value, display = true, color = color)
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    row.forEach { piece ->
                        if (piece.math) {
                            LatexView(
                                latex = piece.value,
                                display = piece.displaySize,
                                inline = true,
                                color = color,
                            )
                        } else if (piece.value.isNotEmpty()) {
                            LinkedText(text = inlineMarkdown(piece.value), style = style, color = color)
                        }
                    }
                }
            }
        }
    }
}

private data class InlinePiece(
    val value: String,
    val math: Boolean = false,
    val displayFence: Boolean = false,
    val displaySize: Boolean = false,
)

private fun splitInlineMath(text: String): List<InlinePiece> {
    val pieces = mutableListOf<InlinePiece>()
    var last = 0
    for (match in inlinePattern.findAll(text)) {
        if (match.range.first > last) pieces += InlinePiece(text.substring(last, match.range.first))
        val token = match.value
        if (token.startsWith("$")) {
            val displayFence = token.startsWith("$$")
            val latex = (if (displayFence) token.removeSurrounding("$$") else token.removeSurrounding("$"))
                .trim()
                .replace("\\$", "$")
            pieces += InlinePiece(
                value = latex,
                math = true,
                displayFence = displayFence,
                displaySize = displayFence || latex.contains("\\displaystyle") || latex.contains("\\dfrac"),
            )
        } else {
            pieces += InlinePiece(token)
        }
        last = match.range.last + 1
    }
    if (last < text.length) pieces += InlinePiece(text.substring(last))
    return pieces
}

private fun List<InlinePiece>.chunkedByText(): List<List<InlinePiece>> {
    val rows = mutableListOf<MutableList<InlinePiece>>()
    var current = mutableListOf<InlinePiece>()
    forEach { piece ->
        if (!piece.math && piece.value.contains('\n')) {
            val parts = piece.value.split('\n')
            parts.forEachIndexed { index, part ->
                if (part.isNotEmpty()) current += InlinePiece(part)
                if (index < parts.lastIndex) {
                    if (current.isNotEmpty()) rows += current
                    current = mutableListOf()
                }
            }
        } else {
            current += piece
        }
    }
    if (current.isNotEmpty()) rows += current
    return rows
}

@Composable
private fun LinkedText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (text.getStringAnnotations("url", 0, text.length).isEmpty()) {
        Text(text = text, style = style, color = color, modifier = modifier)
        return
    }
    val handler = LocalUriHandler.current
    ClickableText(
        text = text,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
                runCatching { handler.openUri(it.item) }
            }
        },
    )
}

@Composable
private fun LatexBlock(latex: String, display: Boolean, color: Color) {
    LatexView(latex = latex, display = display, color = color, modifier = Modifier.fillMaxWidth())
}

private val inlinePattern = Regex(
    """(\*\*[^*]+\*\*)|(\*[^*]+\*)|(`[^`]+`)|(\$\$[\s\S]+?\$\$)|(\$(?:\\\$|[^$\n])+\$)|(\[[^\]]+\]\([^)]+\))"""
)

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    // patterns: **bold**, *italic*, `code`, [label](url)
    val pattern = Regex(
        """(\*\*[^*]+\*\*)|(\*[^*]+\*)|(`[^`]+`)|(\$\$[^$]+\$\$)|(\$[^$]+\$)|(\[[^\]]+\]\([^)]+\))"""
    )
    var last = 0
    for (m in pattern.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x143F8A37),
                    ),
                ) { append(token.removeSurrounding("`")) }
            }
            token.startsWith("$") -> append(renderLatex(token.trim('$')))
            token.startsWith("*") && token.endsWith("*") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
            }
            token.startsWith("[") -> {
                val lm = Regex("""\[([^\]]+)\]\(([^)]+)\)""").matchEntire(token)
                val href = lm?.groupValues?.get(2)
                if (lm != null && href != null && (href.startsWith("http://") || href.startsWith("https://"))) {
                    pushStringAnnotation("url", href)
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF3F8A37),
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) { append(lm.groupValues[1]) }
                    pop()
                } else if (lm != null) {
                    append(lm.groupValues[1])
                } else append(token)
            }
            else -> append(token)
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}


/**
 * Renders the allowed inline HTML as Markdown markers the existing painter
 * already understands. A fragment that fails [parseHtml] is returned unchanged.
 */
internal fun renderHtmlInline(text: String): String {
    if ('<' !in text) return text
    val nodes = parseHtml(text) ?: return text
    return nodes.joinToString("") { renderNode(it) }
}

private fun renderNode(node: HtmlNode): String = when (node) {
    is HtmlNode.Text -> node.value
    is HtmlNode.Element -> {
        val inner = node.children.joinToString("") { renderNode(it) }
        when (node.tag) {
            "b", "strong" -> "**$inner**"
            "i", "em" -> "*$inner*"
            "code" -> "`$inner`"
            "br" -> " "
            "p" -> inner
            "a" -> if (node.href != null) "[$inner](${node.href})" else inner
            else -> inner
        }
    }
}

@Composable
private fun HtmlBlock(source: String, color: Color) {
    val nodes = remember(source) { parseHtml(source) }
    if (nodes == null) {
        Text(text = source, color = color, style = MaterialTheme.typography.bodyLarge)
        return
    }
    Column {
        nodes.forEach { node -> HtmlNodeView(node, color) }
    }
}

@Composable
private fun HtmlNodeView(node: HtmlNode, color: Color) {
    when (node) {
        is HtmlNode.Text -> if (node.value.isNotBlank()) {
            RichMarkdownText(text = node.value, color = color)
        }
        is HtmlNode.Element -> when (node.tag) {
            "details" -> DetailsView(node, color)
            "br" -> Spacer(Modifier.height(8.dp))
            else -> RichMarkdownText(text = renderNode(node), color = color)
        }
    }
}

@Composable
private fun DetailsView(node: HtmlNode.Element, color: Color) {
    val summary = node.children.firstOrNull { it is HtmlNode.Element && it.tag == "summary" }
        ?.let { (it as HtmlNode.Element).children.joinToString("") { child -> renderNode(child) } }
        ?: "详情"
    val body = node.children.filterNot { it is HtmlNode.Element && it.tag == "summary" }
    var open by rememberSaveable(node) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        TextButton(onClick = { open = !open }) {
            Text(text = (if (open) "▾ " else "▸ ") + summary, color = color)
        }
        if (open) {
            body.forEach { HtmlNodeView(it, color) }
        }
    }
}
