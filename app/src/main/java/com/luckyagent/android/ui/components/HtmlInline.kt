package com.luckyagent.android.ui.components

/**
 * The small set of HTML the chat renderer draws. Everything else stays text.
 *
 * No script, no style, no remote resources. A fragment that is unbalanced,
 * nested past [MAX_DEPTH], or contains a tag outside [ALLOWED] is rejected
 * whole so the caller shows the original source.
 */
internal const val HTML_MAX_DEPTH = 8

private val ALLOWED = setOf("b", "strong", "i", "em", "code", "br", "p", "a", "details", "summary")
private val VOID_TAGS = setOf("br")

internal sealed class HtmlNode {
    data class Text(val value: String) : HtmlNode()
    data class Element(
        val tag: String,
        val href: String? = null,
        val children: List<HtmlNode> = emptyList(),
    ) : HtmlNode()
}

/** Parses [source] against the allow-list. Returns null when it should stay text. */
internal fun parseHtml(source: String): List<HtmlNode>? {
    if ('<' !in source) return null
    val tokens = tokenize(source) ?: return null
    return buildTree(tokens)
}

private sealed class Tok {
    data class Text(val value: String) : Tok()
    data class Open(val tag: String, val href: String?) : Tok()
    data class Close(val tag: String) : Tok()
}

private fun tokenize(source: String): List<Tok>? {
    val out = mutableListOf<Tok>()
    var i = 0
    while (i < source.length) {
        val lt = source.indexOf('<', i)
        if (lt < 0) {
            out += Tok.Text(unescape(source.substring(i)))
            break
        }
        if (lt > i) out += Tok.Text(unescape(source.substring(i, lt)))
        val gt = source.indexOf('>', lt + 1)
        if (gt < 0) return null
        val raw = source.substring(lt + 1, gt).trim()
        if (raw.isEmpty() || raw.startsWith("!") || raw.startsWith("?")) return null
        val closing = raw.startsWith("/")
        val selfClose = raw.endsWith("/")
        val body = raw.removePrefix("/").removeSuffix("/").trim()
        val tag = body.substringBefore(' ').substringBefore('\t').lowercase()
        if (tag !in ALLOWED) return null
        if (closing) {
            if (tag in VOID_TAGS) return null
            out += Tok.Close(tag)
        } else {
            val href = if (tag == "a") linkHref(body) else null
            if (tag == "a" && href == null && body.contains("href", ignoreCase = true)) {
                // An href was present but not http(s). Keep the words, drop the link.
                out += Tok.Open("a", null)
            } else {
                out += Tok.Open(tag, href)
            }
            if (selfClose || tag in VOID_TAGS) out += Tok.Close(tag)
        }
        i = gt + 1
    }
    return out
}

private fun linkHref(body: String): String? {
    val match = Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""", RegexOption.IGNORE_CASE)
        .find(body) ?: return null
    val value = (match.groupValues[1].ifEmpty { match.groupValues[2] }).ifEmpty { match.groupValues[3] }
    val href = unescape(value).trim()
    return href.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private fun buildTree(tokens: List<Tok>): List<HtmlNode>? {
    data class Frame(val tag: String, val href: String?, val children: MutableList<HtmlNode>)
    val root = mutableListOf<HtmlNode>()
    val stack = ArrayDeque<Frame>()
    fun add(node: HtmlNode) {
        val frame = stack.lastOrNull()
        if (frame == null) root += node else frame.children += node
    }
    for (tok in tokens) {
        when (tok) {
            is Tok.Text -> if (tok.value.isNotEmpty()) add(HtmlNode.Text(tok.value))
            is Tok.Open -> {
                if (stack.size >= HTML_MAX_DEPTH) return null
                stack.addLast(Frame(tok.tag, tok.href, mutableListOf()))
            }
            is Tok.Close -> {
                val frame = stack.removeLastOrNull() ?: return null
                if (frame.tag != tok.tag) return null
                add(HtmlNode.Element(frame.tag, frame.href, frame.children.toList()))
            }
        }
    }
    if (stack.isNotEmpty()) return null
    return root
}

private fun unescape(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
