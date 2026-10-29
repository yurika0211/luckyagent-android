package com.luckyagent.android.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                MarkdownBlock(
                    block = block,
                    active = active || !blockNeedsLiveSurface(block),
                    color = color,
                    imageHeaders = imageHeaders,
                    imageBaseUrl = imageBaseUrl,
                    htmlNodes = htmlNodes.getOrNull(index).orEmpty(),
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
                    // Parsed once per source. A static drawing renders itself, and the
                    // parse must not run again on every recomposition.
                    val svg = remember(block.source) { staticSvgDocument(block.source) }
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
                    val widths = remember(block.rows) {
                        List(block.alignments.size) { col ->
                            ((block.rows.maxOfOrNull { it.getOrNull(col).orEmpty().length } ?: 0) * 8 + 16).coerceIn(64, 280).dp
                        }
                    }
                    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                        block.rows.forEachIndexed { row, cells -> Row {
                            cells.forEachIndexed { col, cell ->
                                RichMarkdownText(cell, color,
                                    modifier = Modifier.width(widths[col]).border(1.dp, CloverLine).padding(8.dp),
                                    baseUrl = imageBaseUrl, headers = imageHeaders,
                                    precomputedNodes = richNodes[cell],
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = color,
                                        fontWeight = if (row == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = when (block.alignments[col]) {
                                            TableAlignment.CENTER -> TextAlign.Center
                                            TableAlignment.RIGHT -> TextAlign.End
                                            else -> TextAlign.Start
                                        },
                                    ))
                            }
                        } }
                    }
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
    RichAnnotatedText(annotated, style, color, modifier)
}

@Composable
internal fun RichAnnotatedText(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val math = text.getStringAnnotations("math", 0, text.length)
    if (math.isEmpty()) {
        LinkedText(text, style, color, modifier)
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
internal fun LinkedText(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    if (text.getStringAnnotations("url", 0, text.length).isEmpty()) {
        Text(text, style = style, color = color, modifier = modifier)
    } else {
        val handler = LocalUriHandler.current
        ClickableText(text, style = style.copy(color = color), modifier = modifier, onClick = { offset ->
            text.getStringAnnotations("url", offset, offset).firstOrNull()?.let { runCatching { handler.openUri(it.item) } }
        })
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
