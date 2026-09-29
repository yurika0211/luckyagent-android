package com.luckyagent.android.ui.util

data class MessageQuote(
    val messageId: String,
    val role: String,
    val content: String,
)

fun bubbleCopyText(
    role: String,
    content: String,
    toolName: String? = null,
    toolArgs: String? = null,
    toolOutput: String? = null,
): String {
    val normalizedRole = role.trim().ifBlank { "assistant" }
    if (normalizedRole.equals("tool", ignoreCase = true) || !toolName.isNullOrBlank()) {
        val name = toolName?.trim().orEmpty().ifBlank { "tool" }
        val parts = mutableListOf("tool: $name")
        toolArgs?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "args: $it" }
        toolOutput?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "output: $it" }
        content.trim().takeIf { it.isNotEmpty() && !parts.any { part -> part.endsWith(it) } }?.let {
            parts += it
        }
        return parts.joinToString("\n").ifBlank { name }
    }
    return content
}

fun truncateForQuote(content: String, maxChars: Int = 800): String {
    // Keep line breaks so the outbound formatter can produce a real markdown
    // quote block while still avoiding noisy indentation and trailing spaces.
    val normalized = content
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .lineSequence()
        .map { it.trimEnd().replace(Regex("[ \\t]+"), " ") }
        .joinToString("\n")
    if (normalized.length <= maxChars) return normalized
    return normalized.take(maxChars.coerceAtLeast(1)).trimEnd() + "…"
}

fun formatQuoteBlock(role: String, content: String, maxChars: Int = 800): String {
    val label = role.trim().ifBlank { "assistant" }
    val body = truncateForQuote(content, maxChars)
    val quotedLines = if (body.isEmpty()) {
        ">"
    } else {
        body.lineSequence()
            .map { line -> "> ${line.trimEnd()}" }
            .joinToString("\n")
    }
    return buildString {
        appendLine("> [引用-$label]")
        append(quotedLines)
    }.trimEnd()
}

fun buildOutboundMessage(userText: String, quote: MessageQuote?, maxChars: Int = 800): String {
    val text = userText.trim()
    if (quote == null) return text
    val block = formatQuoteBlock(quote.role, quote.content, maxChars)
    return if (text.isEmpty()) {
        "$block\n>\n> ---"
    } else {
        "$block\n>\n> ---\n$text"
    }
}

fun quotePreview(content: String, max: Int = 72): String {
    val normalized = content.trim().replace(Regex("\\s+"), " ")
    if (normalized.length <= max) return normalized
    return normalized.take(max.coerceAtLeast(1)).trimEnd() + "…"
}

fun quoteRoleLabel(role: String): String = when (role.trim().lowercase()) {
    "user" -> "用户"
    "assistant" -> "助手"
    "tool" -> "工具"
    "system" -> "系统"
    "error" -> "错误"
    "reasoning" -> "思考"
    else -> role.trim().ifBlank { "消息" }
}

fun quoteFromBubble(
    id: String,
    role: String,
    content: String,
    toolName: String? = null,
    toolArgs: String? = null,
    toolOutput: String? = null,
): MessageQuote? {
    val copy = bubbleCopyText(
        role = role,
        content = content,
        toolName = toolName,
        toolArgs = toolArgs,
        toolOutput = toolOutput,
    ).trim()
    if (copy.isEmpty()) return null
    val normalizedRole = when {
        !toolName.isNullOrBlank() || role.equals("tool", ignoreCase = true) -> "tool"
        else -> role.trim().ifBlank { "assistant" }
    }
    return MessageQuote(
        messageId = id,
        role = normalizedRole,
        content = truncateForQuote(copy, maxChars = 800),
    )
}
