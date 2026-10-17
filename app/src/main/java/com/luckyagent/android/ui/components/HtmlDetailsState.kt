package com.luckyagent.android.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import java.security.MessageDigest
import java.util.IdentityHashMap

/** Expansion state belongs to the HTML document, independent of the rendering group's position. */
internal class HtmlDetailsState {
    private data class Anchor(val id: String, val signature: String, val bodyHash: String, val prefix: String)
    private var nextId = 0
    private var previous = emptyList<Anchor>()
    private val expanded = mutableStateMapOf<String, Boolean>()

    fun isExpanded(id: String, initiallyOpen: Boolean) = expanded[id] ?: initiallyOpen
    fun toggle(id: String, initiallyOpen: Boolean) { expanded[id] = !isExpanded(id, initiallyOpen) }

    fun identities(nodes: List<HtmlNode>): Map<HtmlNode.Element, String> {
        val details = buildList {
            fun visit(nodes: List<HtmlNode>) {
                nodes.filterIsInstance<HtmlNode.Element>().forEach { node ->
                    if (node.tag == "details") add(node)
                    visit(node.children)
                }
            }
            visit(nodes)
        }
        val incoming = details.map { node ->
            val summary = node.children.firstOrNull { it is HtmlNode.Element && it.tag == "summary" }
            val identity = node.attributes["id"]?.takeIf { it.isNotBlank() }?.let { "id:$it" }
                ?: "summary:" + htmlLiteralText((summary as? HtmlNode.Element)?.children.orEmpty()).trim()
            val body = htmlLiteralText(node.children.filterNot { it === summary }).trim()
            Anchor("", identity, digest(body), body.take(128))
        }
        val unused = previous.toMutableList()
        val matched = arrayOfNulls<Anchor>(incoming.size)
        fun match(index: Int, predicate: (Anchor) -> Boolean) {
            val old = unused.indexOfFirst(predicate)
            if (old >= 0) matched[index] = unused.removeAt(old)
        }
        // Exact bodies preserve identities when same-title siblings are inserted, removed or reordered.
        incoming.forEachIndexed { i, anchor ->
            match(i) { it.signature == anchor.signature && it.bodyHash == anchor.bodyHash }
        }
        // Streaming normally appends to the same body. Match those before assigning new siblings.
        incoming.forEachIndexed { i, anchor ->
            if (matched[i] == null && anchor.prefix.isNotEmpty()) match(i) {
                it.signature == anchor.signature && it.prefix.isNotEmpty() &&
                    (anchor.prefix.startsWith(it.prefix) || it.prefix.startsWith(anchor.prefix))
            }
        }
        // A unique summary or explicit id survives arbitrary body edits as well.
        incoming.forEachIndexed { i, anchor ->
            if (matched[i] == null) match(i) { it.signature == anchor.signature }
        }
        previous = incoming.mapIndexed { i, anchor -> anchor.copy(id = matched[i]?.id ?: "details-${nextId++}") }
        return IdentityHashMap<HtmlNode.Element, String>().apply {
            details.forEachIndexed { i, node -> put(node, previous[i].id) }
        }
    }

    private fun save(): List<String> = buildList {
        add(nextId.toString())
        previous.forEach {
            addAll(listOf(it.id, it.signature, it.bodyHash, it.prefix, expanded[it.id]?.toString().orEmpty()))
        }
    }

    companion object {
        val Saver = Saver<HtmlDetailsState, List<String>>(
            save = { it.save() },
            restore = { saved ->
                HtmlDetailsState().apply {
                    nextId = saved.first().toInt()
                    previous = saved.drop(1).chunked(5).map { fields ->
                        if (fields[4].isNotEmpty()) expanded[fields[0]] = fields[4].toBooleanStrict()
                        Anchor(fields[0], fields[1], fields[2], fields[3])
                    }
                }
            },
        )

        private fun digest(text: String) = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

internal class HtmlDetailsContext(
    val state: HtmlDetailsState,
    val identities: Map<HtmlNode.Element, String>,
)
