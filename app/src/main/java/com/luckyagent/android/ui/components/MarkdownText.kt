package com.luckyagent.android.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.luckyagent.android.ui.theme.*

/** Native Markdown and HTML fragments share one rich-text painter in every context. */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color = CloverText,
    modifier: Modifier = Modifier,
    imageHeaders: Map<String, String> = emptyMap(),
    imageBaseUrl: String = "",
    streaming: Boolean = false,
    active: Boolean = true,
) {
    // Streaming output is intentionally plain text until the turn finishes.
    // Re-parsing a growing body every 40 ms costs more than the transient
    // formatting is worth; the completed body is projected off the UI thread
    // below.
    if (streaming) {
        Text(
            text = markdown,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )
        return
    }
    val parsed by produceState<ParsedMarkdown?>(null, markdown) {
        value = withContext(Dispatchers.Default) {
            MarkdownParseCache.getOrParse(markdown, streaming = false)
        }
    }
    val parsedMarkdown = parsed
    if (parsedMarkdown == null) {
        Text(
            text = markdown,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )
        return
    }
    val blocks = parsedMarkdown.blocks
    val htmlNodes = parsedMarkdown.htmlNodes
    val richNodes = parsedMarkdown.richNodes
    val svgNodes = parsedMarkdown.svgNodes
    val detailsState = rememberSaveable(saver = HtmlDetailsState.Saver) { HtmlDetailsState() }
    val detailsContext = remember(htmlNodes, detailsState) {
        HtmlDetailsContext(detailsState, detailsState.identities(htmlNodes.flatten()))
    }
    // Heavy blocks (WebView, LaTeX) only mount while this message is inside the
    // chat window. A nested lazy list would steal the outer scroll, so the
    // blocks stay in a Column and the row decides `active`.
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            key(index) {
                val liveSurface = blockNeedsLiveSurface(block, svgNodes.getOrNull(index) != null)
                MarkdownBlock(
                    block = block,
                    active = active || !liveSurface,
                    color = color,
                    imageHeaders = imageHeaders,
                    imageBaseUrl = imageBaseUrl,
                    htmlNodes = htmlNodes.getOrNull(index).orEmpty(),
                    svg = svgNodes.getOrNull(index),
                    richNodes = richNodes,
                    detailsContext = detailsContext,
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: MdBlock,
    active: Boolean,
    color: Color,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    htmlNodes: List<HtmlNode>,
    svg: SvgDocument?,
    richNodes: Map<String, List<HtmlNode>>,
    detailsContext: HtmlDetailsContext,
) {
            when (block) {
                is MdBlock.Mermaid -> if (active) {
                    MermaidView(source = block.source)
                } else {
                    HeavyBlockPlaceholder("图表离屏，滚回来再画")
                }
                is MdBlock.Html -> {
                    // The static drawing was projected off the UI thread with the
                    // rest of the block, so recomposition never re-parses it.
                    val heavy = svg != null || block.showPreview || requiresHtmlPage(block.source)
                    if (!active && heavy) {
                        HeavyBlockPlaceholder("预览离屏，滚回来再画")
                    } else if (svg != null) {
                        InlineSvgPreview(source = block.source, svg = svg)
                    } else if (block.showPreview || requiresHtmlPage(block.source)) {
                        InlineHtmlPreview(source = block.source, baseUrl = imageBaseUrl, headers = imageHeaders)
                    } else {
                        NativeHtmlContent(block.source, color, imageHeaders, imageBaseUrl, htmlNodes, detailsContext)
                    }
                }
                is MdBlock.Code -> CodeText(block.body)
                is MdBlock.Image -> MarkdownImage(block.source, block.alt, imageHeaders, imageBaseUrl)
                is MdBlock.Table -> {
                    val widths = remember(block.rows, block.alignments.size) {
                        markdownTableColumnWidths(block.rows, block.alignments.size)
                    }
                    MarkdownTable(
                        block = block,
                        widths = widths,
                        color = color,
                        imageHeaders = imageHeaders,
                        imageBaseUrl = imageBaseUrl,
                        richNodes = richNodes,
                    )
                }
                MdBlock.IncompleteTable -> Unit
                is MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 10.dp), color = CloverLine)
                is MdBlock.Math -> if (active) {
                    LatexView(block.latex, block.display, color, Modifier.fillMaxWidth())
                } else {
                    HeavyBlockPlaceholder(block.latex)
                }
                is MdBlock.Heading -> RichMarkdownText(block.text, color,
                    baseUrl = imageBaseUrl, headers = imageHeaders,
                    precomputedNodes = richNodes[block.text],
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    }.copy(color = color, fontWeight = FontWeight.SemiBold))
                is MdBlock.ListItem -> Row(Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("${block.marker} ", color = color)
                    RichMarkdownText(
                        block.text,
                        color,
                        baseUrl = imageBaseUrl,
                        headers = imageHeaders,
                        precomputedNodes = richNodes[block.text],
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Paragraph -> RichMarkdownText(
                    block.text,
                    color,
                    baseUrl = imageBaseUrl,
                    headers = imageHeaders,
                    precomputedNodes = richNodes[block.text],
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
}

/**
 * Column width in dp from cell text. A CJK or fullwidth glyph counts as two
 * Latin units at 8dp each, plus horizontal padding. The old `length * 8`
 * clamp at 280dp cut Chinese cells inside the column, so horizontal scroll
 * could not reveal them.
 */
internal fun markdownTableColumnWidthDp(rows: List<List<String>>, column: Int): Int {
    val units = rows.maxOfOrNull { markdownTableDisplayUnits(it.getOrNull(column).orEmpty()) } ?: 0
    return (units * 8 + 16).coerceAtLeast(64)
}

internal fun markdownTableColumnWidths(rows: List<List<String>>, columns: Int): List<Dp> =
    List(columns) { col -> markdownTableColumnWidthDp(rows, col).dp }

/** Latin letters count as 1. CJK and fullwidth count as 2. */
internal fun markdownTableDisplayUnits(text: String): Int {
    var units = 0
    var index = 0
    while (index < text.length) {
        val code = text.codePointAt(index)
        units += if (markdownTableGlyphIsWide(code)) 2 else 1
        index += Character.charCount(code)
    }
    return units
}

private fun markdownTableGlyphIsWide(code: Int): Boolean = when (code) {
    in 0x1100..0x115F,
    in 0x2329..0x232A,
    in 0x2E80..0xA4CF,
    in 0xAC00..0xD7A3,
    in 0xF900..0xFAFF,
    in 0xFE10..0xFE19,
    in 0xFE30..0xFE6F,
    in 0xFF00..0xFF60,
    in 0xFFE0..0xFFE6,
    in 0x1F200..0x1F251,
    in 0x1F300..0x1FAFF,
    in 0x20000..0x3FFFD,
    -> true
    else -> false
}

@Composable
private fun MarkdownTable(
    block: MdBlock.Table,
    widths: List<Dp>,
    color: Color,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    richNodes: Map<String, List<HtmlNode>>,
) {
    val grid = CloverLine
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRect(grid, style = Stroke(width = stroke))
            },
    ) {
        block.rows.forEachIndexed { row, cells ->
            Row {
                val count = maxOf(cells.size, widths.size)
                for (col in 0 until count) {
                    val cell = cells.getOrNull(col).orEmpty()
                    val width = widths.getOrElse(col) { 64.dp }
                    Box(
                        Modifier
                            .widthIn(min = width, max = width)
                            .drawBehind {
                                val stroke = 1.dp.toPx()
                                if (col > 0) {
                                    drawLine(grid, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = stroke)
                                }
                                if (row > 0) {
                                    drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = stroke)
                                }
                            }
                            .padding(8.dp)
                            .clipToBounds(),
                    ) {
                        RichMarkdownText(
                            cell,
                            color,
                            baseUrl = imageBaseUrl,
                            headers = imageHeaders,
                            precomputedNodes = richNodes[cell],
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = color,
                                fontWeight = if (row == 0) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = when (block.alignments.getOrNull(col)) {
                                    TableAlignment.CENTER -> TextAlign.Center
                                    TableAlignment.RIGHT -> TextAlign.End
                                    else -> TextAlign.Start
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeavyBlockPlaceholder(label: String) {
    Text(
        label,
        color = CloverText2,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(CloverBgSide, RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

@Composable
internal fun CodeText(source: String, modifier: Modifier = Modifier) {
    // SelectionContainer walks the whole subtree. One per code fence makes a
    // long reply expensive to scroll; the message body stays selectable.
    // A horizontal scroller is only worth it when a line can actually overflow.
    // Short fences used to pay for one on every block.
    val wide = source.lineSequence().any { it.length > 72 }
    val scroll = if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier
    Text(
        source,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = CloverText2),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(CloverBgSide, RoundedCornerShape(8.dp))
            .border(1.dp, CloverLine, RoundedCornerShape(8.dp))
            .then(scroll)
            .padding(10.dp),
    )
}

@Composable
internal fun MarkdownImage(source: String, alt: String, headers: Map<String, String>, baseUrl: String) {
    val context = LocalContext.current
    val resolved = remember(source, baseUrl) { resolveHtmlUrl(source, baseUrl) }
    val artifact = resolved != null && isArtifactResourceUrl(resolved, baseUrl)
    var approved by remember(resolved) { mutableStateOf(false) }
    var failed by remember(resolved, source, headers, baseUrl) { mutableStateOf(false) }
    val loader = remember(resolved, headers, baseUrl) { HtmlResourceLoader(headers, baseUrl) }
    DisposableEffect(loader) { onDispose { loader.close() } }
    val bitmap by produceState<android.graphics.Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) { if (source.startsWith("data:image/")) decodeMarkdownImage(source) else null }
    }
    val artifactBytes by produceState<ByteArray?>(null, resolved, artifact, loader) {
        value = null
        if (artifact) {
            try { value = withContext(Dispatchers.IO) { loader.fetch(resolved).bytes } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failed = true }
        }
    }
    when {
        bitmap != null -> Image(bitmap!!.asImageBitmap(), alt, Modifier.fillMaxWidth().padding(vertical = 6.dp))
        resolved != null && !artifact && !approved -> TextButton(onClick = { approved = true }) {
            Text(alt.takeIf { it.isNotBlank() }?.let { "$it · 加载远程图片" } ?: "加载远程图片")
        }
        artifact && artifactBytes == null && !failed -> CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp))
        resolved != null && !failed -> coil.compose.AsyncImage(
            model = remember(resolved, artifactBytes) {
                // Authenticated images use the same redirect policy as HTML previews.
                // Coil only decodes their bytes; external images require a gesture and carry no API headers.
                ImageRequest.Builder(context).data(if (artifact) artifactBytes else resolved).build()
            },
            contentDescription = alt,
            onError = { failed = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 480.dp).padding(vertical = 6.dp))
        else -> Text(alt.ifBlank { "图片无法加载" }, style = MaterialTheme.typography.bodySmall, color = CloverText2)
    }
}

@Composable
internal fun htmlColorPalette(): Map<String, Color> = mapOf(
    "accent" to MaterialTheme.colorScheme.primary,
    "muted" to MaterialTheme.colorScheme.onSurfaceVariant,
    "success" to CloverAccent,
    "warning" to CloverWarning,
    "error" to MaterialTheme.colorScheme.error,
)

@Composable
private fun RichMarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
    headers: Map<String, String> = emptyMap(),
    precomputedNodes: List<HtmlNode>? = null,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
) {
    val colors = htmlColorPalette()
    val nodes = precomputedNodes ?: remember(text) { richTextNodes(text) }
    fun hasMedia(nodes: List<HtmlNode>): Boolean = nodes.any { it is HtmlNode.Element &&
        (it.tag in setOf("img", "details", "table", "ul", "ol", "pre") || hasMedia(it.children)) }
    if (hasMedia(nodes)) {
        Box(modifier) { HtmlFlow(nodes, color, headers, baseUrl, style) }
        return
    }
    val annotated = remember(nodes, colors, baseUrl) { htmlAnnotatedText(nodes, colors, baseUrl, parseMath = false) }
    RichAnnotatedText(annotated, style, color, modifier, softWrap, overflow)
}

@Composable
internal fun RichAnnotatedText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val math = text.getStringAnnotations("math", 0, text.length)
    if (math.isEmpty()) {
        LinkedText(text, style, color, modifier, softWrap, overflow)
    } else {
        val runs = mutableListOf<InlineMathRun>()
        var cursor = 0
        math.forEach { span ->
            if (cursor < span.start) runs += InlineMathRun.Text(text.subSequence(cursor, span.start))
            val latex = text.text.substring(span.start, span.end)
            runs += InlineMathRun.Math(latex, span.item == "display" || latex.contains("\\displaystyle") || latex.contains("\\dfrac"))
            cursor = span.end
        }
        if (cursor < text.length) runs += InlineMathRun.Text(text.subSequence(cursor, text.length))
        InlineMathText(runs, style, color, modifier.fillMaxWidth())
    }
}

@Composable
internal fun LinkedText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (text.getStringAnnotations("url", 0, text.length).isEmpty()) {
        Text(text, style = style, color = color, modifier = modifier, softWrap = softWrap, overflow = overflow)
    } else {
        val handler = LocalUriHandler.current
        var browserUrl by remember { mutableStateOf<String?>(null) }
        browserUrl?.let { InAppBrowser(it) { browserUrl = null } }
        ClickableText(
            text,
            style = style.copy(color = color),
            modifier = modifier,
            softWrap = softWrap,
            overflow = overflow,
            onClick = { offset ->
                text.getStringAnnotations("url", offset, offset).firstOrNull()?.let { link ->
                    val page = inAppBrowserUrl(link.item)
                    if (page != null) browserUrl = page else runCatching { handler.openUri(link.item) }
                }
            },
        )
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
