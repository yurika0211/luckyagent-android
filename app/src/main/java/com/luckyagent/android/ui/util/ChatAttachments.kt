package com.luckyagent.android.ui.util

import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.ui.ChatMedia
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private val attachmentJson = Json { ignoreUnknownKeys = true }

/** Assistant prose never becomes a file card. Delivery is attachments[] only. */
internal fun chatMediaFromAssistantText(text: String): List<ChatMedia> = emptyList()

/** Cards from an attachments / files / media array. Body text is ignored. */
internal fun chatMediaFromAttachmentJson(raw: String): List<ChatMedia> {
    val root = runCatching { attachmentJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val items = (root["attachments"] ?: root["files"] ?: root["media"]) as? JsonArray ?: return emptyList()
    return items.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val descriptor = MediaAttachment(
            type = item.stringOrNull("type") ?: "document",
            fileId = item.stringOrNull("file_id"),
            fileUrl = item.stringOrNull("file_url") ?: item.stringOrNull("url") ?: item.stringOrNull("download_url"),
            filePath = item.stringOrNull("file_path"),
            fileName = item.stringOrNull("file_name") ?: item.stringOrNull("name"),
            mimeType = item.stringOrNull("mime_type") ?: item.stringOrNull("content_type"),
            fileSize = (item["file_size"] as? JsonPrimitive)?.longOrNull
                ?: (item["size"] as? JsonPrimitive)?.longOrNull,
        )
        if (descriptor.fileUrl.isNullOrBlank() && descriptor.filePath.isNullOrBlank()) null
        else ChatMedia(descriptor)
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
