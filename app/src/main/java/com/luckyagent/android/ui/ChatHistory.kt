package com.luckyagent.android.ui

import com.luckyagent.android.data.api.ProviderMessage

/**
 * Project each message once and join tool results through their most recent
 * call/result index. Searching all prior bubbles for every tool result becomes
 * quadratic for long histories or results whose call is outside the page.
 */
internal fun projectHistoryBubbles(
    messages: List<ProviderMessage>,
    startIndex: Int = 0,
    project: (ProviderMessage, Int) -> List<ChatBubble> = { message, index -> message.toHistoryBubbles(index) },
): List<ChatBubble> {
    val bubbles = mutableListOf<ChatBubble>()
    val toolIndices = HashMap<String, Int>()
    messages.forEachIndexed { index, message ->
        project(message, startIndex + index).forEach { bubble ->
            val callIndex = if (message.role == "tool" && bubble.role == "tool" && !message.toolCallId.isNullOrBlank()) {
                toolIndices[message.toolCallId]
            } else null
            if (callIndex != null) {
                val call = bubbles[callIndex]
                bubbles[callIndex] = call.copy(
                    content = bubble.content,
                    toolOutput = bubble.toolOutput,
                    attachments = if (bubble.attachments.isNotEmpty()) bubble.attachments else call.attachments,
                    // A call lives on the assistant row; its result owns these
                    // fields, including the ordinal used to expand a preview.
                    contentTruncated = bubble.contentTruncated,
                    historyIndex = bubble.historyIndex,
                )
            } else {
                bubbles += bubble
                if (bubble.role == "tool" && !bubble.stepId.isNullOrBlank()) {
                    toolIndices[bubble.stepId] = bubbles.lastIndex
                }
            }
        }
    }
    return bubbles
}

/**
 * Shared history projection, kept pure so tool-round commentary and final answers can be regression tested.
 * Every bubble carries [idx], the session-wide oldest-first index of the server
 * message it came from. That index is the identity a live turn is reconciled
 * against; a page offset moves when the window moves, this does not.
 */
internal fun ProviderMessage.toHistoryBubbles(
    idx: Int,
    messageAttachments: List<ChatMedia> = emptyList(),
    compactLabel: String? = null,
): List<ChatBubble> {
    val role = role ?: "assistant"
    val base = capUiText(content.orEmpty(), MAX_UI_MESSAGE_CHARS)
    val bubbleBase = clientMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: "h-$idx"
    val result = mutableListOf<ChatBubble>()
    reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
        result += ChatBubble(
            id = "$bubbleBase-reasoning",
            role = "reasoning",
            content = capUiText(reasoning, MAX_UI_REASONING_CHARS),
            reasoningHasContent = true,
            serverOrdinal = idx,
        )
    }
    toolCalls.forEachIndexed { toolIndex, tool ->
        result += ChatBubble(
            id = "$bubbleBase-tool-$toolIndex",
            role = "tool",
            content = buildToolContent(
                name = tool.name ?: "tool",
                args = capUiText(tool.arguments.orEmpty(), MAX_UI_TOOL_ARGS_CHARS),
                output = null,
                done = true,
                success = null,
            ),
            toolName = tool.name,
            toolArgs = capUiText(tool.arguments.orEmpty(), MAX_UI_TOOL_ARGS_CHARS).takeIf { tool.arguments != null },
            toolDone = true,
            stepId = tool.id,
            attachments = messageAttachments,
            serverOrdinal = idx,
        )
    }
    if (role == "tool") {
        result += ChatBubble(
            id = "$bubbleBase-tool-result",
            role = "tool",
            content = capUiText(base, MAX_UI_TOOL_OUTPUT_CHARS),
            toolName = name ?: "tool",
            toolOutput = capUiText(base, MAX_UI_TOOL_OUTPUT_CHARS).takeIf { it.isNotBlank() },
            toolDone = true,
            stepId = toolCallId,
            attachments = messageAttachments,
            contentTruncated = contentTruncated,
            historyIndex = idx,
            serverOrdinal = idx,
        )
    } else if (role == "system" && compactLabel != null) {
        result += ChatBubble(
            id = "$bubbleBase-compact",
            role = "compact",
            content = compactLabel,
            createdAt = createdAt,
            serverOrdinal = idx,
        )
    } else if (base.isNotBlank() || (result.isEmpty() && toolCalls.isEmpty())) {
        result += ChatBubble(
            id = if (role.equals("user", ignoreCase = true)) bubbleBase else "$bubbleBase-${role.hashCode()}",
            role = role,
            content = base,
            createdAt = createdAt,
            usage = usage,
            attachments = messageAttachments,
            isIntermediate = role == "assistant" && toolCalls.isNotEmpty(),
            contentTruncated = contentTruncated,
            historyIndex = idx,
            outboundId = clientMessageId,
            serverOrdinal = idx,
        )
    }
    return result
}

internal fun buildToolContent(
    name: String,
    args: String?,
    output: String?,
    done: Boolean,
    success: Boolean?,
): String {
    val status = when {
        !done -> "running"
        success == false -> "failed"
        else -> "done"
    }
    val parts = mutableListOf("$name · $status")
    if (!args.isNullOrBlank()) parts += "args: ${args.take(400)}"
    if (!output.isNullOrBlank()) parts += "out: ${output.take(600)}"
    return parts.joinToString("\n")
}
