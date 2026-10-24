package com.luckyagent.android.ui.components

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/**
 * A self-contained SVG parsed once per message source.
 *
 * The root is parsed with the XML parser after the HTML parser has validated the
 * surrounding fragment. SVG attributes such as viewBox and preserveAspectRatio
 * are case-sensitive, so the WebView must receive an XML-preserved tree.
 */
internal class SvgDocument(
    internal val root: Element,
    internal val styles: String,
    internal val frame: SvgFrame?,
)

internal data class SvgFrame(
    val width: Float,
    val height: Float,
)

internal fun staticSvgDocument(source: String): SvgDocument? {
    if (source.length > INLINE_HTML_ACTIVE_LIMIT) return null
    if (!source.contains("<svg", ignoreCase = true)) return null
    val graphicSource = graphicSource(source) ?: return null
    val doc = Jsoup.parse(source)
    if (doc.head().select("link, script, base, iframe, object, embed").isNotEmpty()) return null
    if (doc.body().childNodes().any { it is TextNode && it.text().isNotBlank() }) return null
    val drawn = doc.body().children()
    if (drawn.size != 1) return null
    val htmlRoot = drawn[0]
    if (htmlRoot.normalName() != "svg") return null
    if (htmlRoot.select("script, foreignObject, iframe, object, embed, video, audio").isNotEmpty()) return null
    if (htmlRoot.getElementsByAttribute("src").isNotEmpty()) return null
    if (hasExternalReference(htmlRoot)) return null
    if (hasEventHandler(htmlRoot)) return null

    val xmlRoot = Jsoup.parse(graphicSource, "", Parser.xmlParser()).selectFirst("svg") ?: return null
    val frame = frameOf(xmlRoot) ?: return null
    val styles = doc.head().select("style").joinToString("\n") { it.data() }
    return SvgDocument(root = xmlRoot, styles = styles, frame = frame)
}

/** Card height for the full graphic. Tall drawings remain reachable inside the card. */
internal fun svgCardHeight(frame: SvgFrame?, widthDp: Float, viewportDp: Float): Float {
    val width = widthDp.coerceAtLeast(1f)
    val ratio = if (frame == null || frame.width <= 0f) 1f else frame.height / frame.width
    val natural = width * ratio
    val cap = viewportDp.coerceAtLeast(160f) * 0.72f
    return natural.coerceIn(72f, cap)
}

internal fun svgCardScrolls(frame: SvgFrame?, widthDp: Float, viewportDp: Float): Boolean {
    if (frame == null || frame.width <= 0f) return false
    val natural = widthDp.coerceAtLeast(1f) * (frame.height / frame.width)
    return natural > svgCardHeight(frame, widthDp, viewportDp) + 1f
}

/** Measure the root graphic once. Scanning every node made large drawings stall the chat. */
internal const val SVG_SIZE_JS =
    "(function(){var s=document.documentElement;if(!s||s.tagName.toLowerCase()!=='svg')return '0,0';" +
        "var b=s.getBBox();var w=b.width,h=b.height;" +
        "if(!(w>0)||!(h>0)){w=s.clientWidth;h=s.clientHeight}" +
        "return Math.ceil(w)+','+Math.ceil(h)})()"

internal fun svgPreviewDocument(document: SvgDocument, fit: Boolean): String {
    val doc = Document.createShell("")
    doc.select("base").remove()
    doc.head().prependElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
    doc.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr(
        "content",
        "default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:; " +
            "connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'",
    )
    if (document.styles.isNotBlank()) doc.head().appendElement("style").appendText(document.styles)
    doc.head().appendElement("style").appendText(
        "html,body{margin:0;background:transparent}" + if (fit) {
            "html,body{width:100%;height:100%;overflow:hidden}" +
                "svg{display:block;width:100%;height:100%;max-width:100%;max-height:100%}"
        } else {
            "html,body{overflow:auto}svg{display:block;width:100%;height:auto;max-width:none}"
        },
    )
    // xmlRoot preserves viewBox/preserveAspectRatio/xlink names and is cloned only
    // after XML parsing, avoiding the lossy HTML parse -> HTML serialization round trip.
    doc.body().appendChild(document.root.clone())
    return doc.outerHtml()
}

private fun graphicSource(source: String): String? {
    val start = source.indexOf("<svg", ignoreCase = true)
    val end = source.lastIndexOf("</svg>", ignoreCase = true)
    if (start < 0 || end < start) return null
    return source.substring(start, end + "</svg>".length)
}

private fun frameOf(root: Element): SvgFrame? {
    viewBoxFrame(root.attr("viewBox"))?.let { return it }
    val width = svgLength(root.attr("width"))
    val height = svgLength(root.attr("height"))
    if (width == null || height == null || width <= 0f || height <= 0f) return null
    return SvgFrame(width, height)
}

private fun viewBoxFrame(value: String): SvgFrame? {
    val parts = value.trim().split(SVG_SEPARATOR).mapNotNull { it.toFloatOrNull() }
    if (parts.size != 4) return null
    val width = parts[2]
    val height = parts[3]
    if (width <= 0f || height <= 0f) return null
    return SvgFrame(width, height)
}

private val SVG_SEPARATOR = Regex("""[\s,]+""")

private fun svgLength(value: String): Float? {
    val number = Regex("""^\s*([0-9]*\.?[0-9]+)""").find(value)?.groupValues?.get(1) ?: return null
    return number.toFloatOrNull()?.takeIf { it > 0f }
}

private fun hasExternalReference(root: Element): Boolean {
    for (element in root.allElements) {
        for (name in listOf("href", "xlink:href")) {
            if (!element.hasAttr(name)) continue
            if (!element.attr(name).trim().startsWith("#")) return true
        }
    }
    return false
}

private fun hasEventHandler(root: Element): Boolean =
    root.allElements.any { element ->
        element.attributes().any { it.key.startsWith("on", ignoreCase = true) }
    }
