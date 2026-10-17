package com.luckyagent.android.ui

import com.luckyagent.android.data.api.ProviderMessage

/** Shared history projection, kept pure so tool-round commentary and final answers can be regression tested. */
internal fun ProviderMessage.toHistoryBubbles(
    idx: Int,
    messageAttachments: List<ChatMedia> = emptyList(),
    compactLabel: String? = null,
): List<ChatBubble> {
    val role = role ?: "assistant"
    val base = capUiText(content.orEmpty(), MAX_UI_MESSAGE_CHARS)
    val result = mutableListOf<ChatBubble>()
    reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
        result += ChatBubble(
            id = "h-$idx-reasoning",
            role = "reasoning",
            content = capUiText(reasoning, MAX_UI_REASONING_CHARS),
            reasoningHasContent = true,
        )
    }
    toolCalls.forEachIndexed { toolIndex, tool ->
        result += ChatBubble(
            id = "h-$idx-tool-$toolIndex",
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
        )
    }
    if (role == "tool") {
        result += ChatBubble(
            id = "h-$idx-tool-result",
            role = "tool",
            content = capUiText(base, MAX_UI_TOOL_OUTPUT_CHARS),
            toolName = name ?: "tool",
            toolOutput = capUiText(base, MAX_UI_TOOL_OUTPUT_CHARS).takeIf { it.isNotBlank() },
            toolDone = true,
            attachments = messageAttachments,
        )
    } else if (role == "system" && compactLabel != null) {
        result += ChatBubble(
            id = "h-$idx-compact",
            role = "compact",
            content = compactLabel,
            createdAt = createdAt,
        )
    } else if (base.isNotBlank() || (result.isEmpty() && toolCalls.isEmpty())) {
        result += ChatBubble(
            id = "h-$idx-${role.hashCode()}",
            role = role,
            content = base,
            createdAt = createdAt,
            usage = usage,
            attachments = messageAttachments,
            isIntermediate = role == "assistant" && toolCalls.isNotEmpty(),
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
