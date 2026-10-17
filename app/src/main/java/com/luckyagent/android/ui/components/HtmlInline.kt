package com.luckyagent.android.ui.components

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val HTML_MAX_DEPTH = 32

internal enum class HtmlColor {
    ACCENT, MUTED, SUCCESS, WARNING, ERROR;
    companion object {
        fun parse(value: String): HtmlColor? = entries.firstOrNull { it.name.equals(value.trim(), true) }
    }
}

/** Shared native rich-text tree. Text is decoded once and never parsed as markup again. */
internal sealed class HtmlNode {
    data class Text(val value: String) : HtmlNode()
    data class Element(
        val tag: String,
        val href: String? = null,
        val color: HtmlColor? = null,
        val children: List<HtmlNode> = emptyList(),
        val attributes: Map<String, String> = emptyMap(),
    ) : HtmlNode()
}

private val hiddenTags = setOf("script", "style", "head", "template", "iframe", "object")

/** HTML5 parsing handles entities, quoted attributes and unfinished streaming fragments. */
internal fun parseHtml(source: String): List<HtmlNode>? {
    if ('<' !in source) return null
    val document = Jsoup.parseBodyFragment(source)
    fun convert(node: org.jsoup.nodes.Node, depth: Int): HtmlNode? = when (node) {
        is TextNode -> HtmlNode.Text(node.wholeText)
        is Element -> {
            val tag = node.normalName()
            when {
                tag in hiddenTags -> null
                depth >= HTML_MAX_DEPTH -> HtmlNode.Text(node.wholeText())
                else -> HtmlNode.Element(
                    tag = tag,
                    href = node.attr("href").takeIf { it.isNotBlank() },
                    color = HtmlColor.parse(node.attr("data-color")),
                    attributes = node.attributes().associate { it.key.lowercase() to it.value },
                    children = node.childNodes().mapNotNull { convert(it, depth + 1) },
                )
            }
        }
        else -> null
    }
    return document.body().childNodes().mapNotNull { convert(it, 0) }
}

internal fun resolveHtmlUrl(source: String, baseUrl: String): String? =
    (source.trim().toHttpUrlOrNull() ?: baseUrl.toHttpUrlOrNull()?.resolve(source.trim()))?.toString()

internal fun sameHtmlOrigin(source: String, baseUrl: String): Boolean = runCatching {
    val target = URI(source)
    val base = URI(baseUrl)
    fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
    target.host != null && target.scheme.equals(base.scheme, true) &&
        target.host.equals(base.host, true) && port(target) == port(base)
}.getOrDefault(false)

internal fun requiresHtmlPage(source: String): Boolean = Regex(
    """<!doctype|<(?:html|head|body|style|script|svg|canvas|form|input|button|iframe)\b|(?:display\s*:\s*(?:grid|flex)|position\s*:\s*(?:absolute|fixed))""",
    RegexOption.IGNORE_CASE,
).containsMatchIn(source)

internal fun isHtmlAttachment(mimeType: String?, name: String?): Boolean =
    mimeType?.substringBefore(';')?.trim()?.equals("text/html", true) == true ||
        name.orEmpty().substringBefore('?').substringBefore('#').lowercase().let { it.endsWith(".html") || it.endsWith(".htm") }
